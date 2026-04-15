/*
 * Copyright 2026 Aleksei Kuleshov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contact: alex@kuleshov.tech
 */

package me.golemcore.brain.application.service.index;

import me.golemcore.brain.application.port.out.LlmEmbeddingPort;
import me.golemcore.brain.application.port.out.LlmSettingsRepository;
import me.golemcore.brain.application.port.out.WikiDocumentCatalogPort;
import me.golemcore.brain.application.port.out.WikiEmbeddingIndexPort;
import me.golemcore.brain.application.port.out.WikiFullTextIndexPort;
import me.golemcore.brain.domain.Secret;
import me.golemcore.brain.domain.WikiDocumentChangeSet;
import me.golemcore.brain.domain.WikiEmbeddingDocument;
import me.golemcore.brain.domain.WikiEmbeddingSearchHit;
import me.golemcore.brain.domain.WikiIndexStatus;
import me.golemcore.brain.domain.WikiIndexedDocument;
import me.golemcore.brain.domain.WikiNodeKind;
import me.golemcore.brain.domain.WikiSearchHit;
import me.golemcore.brain.domain.WikiSemanticSearchResult;
import me.golemcore.brain.domain.llm.LlmApiType;
import me.golemcore.brain.domain.llm.LlmEmbeddingRequest;
import me.golemcore.brain.domain.llm.LlmEmbeddingResponse;
import me.golemcore.brain.domain.llm.LlmModelConfig;
import me.golemcore.brain.domain.llm.LlmModelKind;
import me.golemcore.brain.domain.llm.LlmProviderConfig;
import me.golemcore.brain.domain.llm.LlmSettings;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiIndexingServiceTest {

    private InMemoryDocumentCatalog documentCatalog;
    private InMemoryFullTextIndex fullTextIndex;
    private InMemoryEmbeddingIndex embeddingIndex;
    private InMemoryLlmSettingsRepository llmSettingsRepository;
    private CountingEmbeddingPort embeddingPort;
    private CountingExecutor indexingExecutor;
    private WikiIndexingService service;

    @BeforeEach
    void setUp() {
        documentCatalog = new InMemoryDocumentCatalog();
        fullTextIndex = new InMemoryFullTextIndex();
        embeddingIndex = new InMemoryEmbeddingIndex();
        llmSettingsRepository = new InMemoryLlmSettingsRepository();
        embeddingPort = new CountingEmbeddingPort();
        indexingExecutor = new CountingExecutor();
        service = new WikiIndexingService(
                documentCatalog,
                fullTextIndex,
                embeddingIndex,
                llmSettingsRepository,
                embeddingPort,
                indexingExecutor);
    }

    @Test
    void shouldSynchronizeChangedDocumentsAndRemoveDeletedPaths() {
        documentCatalog.documents = List.of(
                document("root", "root-revision"),
                document("docs/guide", "guide-revision"));
        fullTextIndex.indexedRevisions = Map.of(
                "root", "old-root-revision",
                "docs/deleted", "deleted-revision");

        service.synchronizeSpace("space-1");

        assertEquals(List.of("root", "docs/guide"), fullTextIndex.upsertedPaths);
        assertEquals(List.of("docs/deleted"), fullTextIndex.deletedPaths);
        assertEquals(0, embeddingPort.callCount);
    }

    @Test
    void shouldProduceMultipleEmbeddingChunksForLongDocuments() {
        WikiIndexingService chunkingService = new WikiIndexingService(
                documentCatalog,
                fullTextIndex,
                embeddingIndex,
                llmSettingsRepository,
                embeddingPort,
                indexingExecutor,
                new WikiDocumentChunker(30, 5));
        documentCatalog.documents = List.of(WikiIndexedDocument.builder()
                .id("docs/long")
                .path("docs/long")
                .title("Long")
                .body("abcdefghijklmnopqrstuvwxyz0123456789abcdefghijklmnopqrstuvwxyz")
                .kind(WikiNodeKind.PAGE)
                .updatedAt(Instant.parse("2026-04-11T00:00:00Z"))
                .revision("rev-1")
                .build());
        fullTextIndex.indexedRevisions = Map.of();
        llmSettingsRepository.settings = embeddingSettings();

        chunkingService.synchronizeSpace("space-1");

        assertTrue(embeddingIndex.lastEmbeddingUpserts.size() >= 2,
                "expected multiple chunks, got " + embeddingIndex.lastEmbeddingUpserts.size());
        for (int chunkIndex = 0; chunkIndex < embeddingIndex.lastEmbeddingUpserts.size(); chunkIndex++) {
            WikiEmbeddingDocument upsert = embeddingIndex.lastEmbeddingUpserts.get(chunkIndex);
            assertEquals(chunkIndex, upsert.getChunkIndex());
            assertEquals("docs/long", upsert.getDocument().getPath());
            assertTrue(upsert.getChunkText() != null && !upsert.getChunkText().isEmpty());
        }
        assertEquals(List.of("docs/long"), embeddingIndex.upsertedPaths);
    }

    @Test
    void shouldPrefixTitleOnEveryChunkEmbedInputWhileKeepingRawChunkText() {
        // Prepending the title to every chunk's embedding input keeps dense
        // retrieval grounded in the document's topic even when a mid-body
        // chunk never mentions the title word — without polluting the stored
        // excerpt with the title prefix.
        WikiIndexingService chunkingService = new WikiIndexingService(
                documentCatalog,
                fullTextIndex,
                embeddingIndex,
                llmSettingsRepository,
                embeddingPort,
                indexingExecutor,
                new WikiDocumentChunker(40, 5));
        String body = "first paragraph about topic one.\n\nsecond paragraph about topic two.\n\n"
                + "third paragraph about topic three.";
        documentCatalog.documents = List.of(WikiIndexedDocument.builder()
                .id("docs/guide")
                .path("docs/guide")
                .title("Onboarding Playbook")
                .body(body)
                .kind(WikiNodeKind.PAGE)
                .updatedAt(Instant.parse("2026-04-11T00:00:00Z"))
                .revision("rev-1")
                .build());
        fullTextIndex.indexedRevisions = Map.of();
        llmSettingsRepository.settings = embeddingSettings();

        chunkingService.synchronizeSpace("space-1");

        assertTrue(embeddingPort.lastInputs.size() >= 2,
                "expected multi-chunk input, got " + embeddingPort.lastInputs.size());
        for (String input : embeddingPort.lastInputs) {
            assertTrue(input.startsWith("Onboarding Playbook"),
                    "every chunk embed input should be prefixed with title, got: " + input);
        }
        for (WikiEmbeddingDocument upsert : embeddingIndex.lastEmbeddingUpserts) {
            assertFalse(upsert.getChunkText().startsWith("Onboarding Playbook"),
                    "stored chunk text should be the raw body slice, got: " + upsert.getChunkText());
        }
    }

    @Test
    void shouldRebuildEmbeddingsWhenEmbeddingModelChanges() {
        // If the configured embedding model changes, previously stored
        // vectors are geometrically incompatible with the new query embeddings.
        // synchronizeSpace must invalidate them even when document revisions
        // are unchanged.
        documentCatalog.documents = List.of(document("docs/guide", "guide-revision"));
        fullTextIndex.indexedRevisions = Map.of("docs/guide", "guide-revision");
        embeddingIndex.indexedRevisions = Map.of("docs/guide", "guide-revision");
        embeddingIndex.storedEmbeddingModelId = "old-embedding-model";
        llmSettingsRepository.settings = embeddingSettings();

        service.synchronizeSpace("space-1");

        assertEquals(1, embeddingPort.callCount,
                "embedding provider should be called to rebuild vectors for the new model");
        assertEquals(List.of("docs/guide"), embeddingIndex.upsertedPaths);
    }

    @Test
    void shouldReportEmbeddingsNotReadyWhenStoredModelDiffersFromConfigured() {
        documentCatalog.documents = List.of(document("docs/guide", "guide-revision"));
        fullTextIndex.indexedRevisions = Map.of("docs/guide", "guide-revision");
        embeddingIndex.upsertedPaths = List.of("docs/guide");
        embeddingIndex.storedEmbeddingModelId = "old-embedding-model";
        llmSettingsRepository.settings = embeddingSettings();

        assertFalse(service.getStatus("space-1").isEmbeddingsReady(),
                "embeddingsReady should be false when stored model id differs from current");
    }

    @Test
    void shouldPopulateEmbeddingIndexWhenEmbeddingModelIsConfigured() {
        documentCatalog.documents = List.of(document("docs/guide", "guide-revision"));
        fullTextIndex.indexedRevisions = Map.of();
        llmSettingsRepository.settings = embeddingSettings();

        service.synchronizeSpace("space-1");

        assertEquals(1, embeddingPort.callCount);
        assertEquals(List.of("docs/guide"), embeddingIndex.upsertedPaths);
        assertTrue(service.getStatus("space-1").isEmbeddingsReady());
    }

    @Test
    void shouldSearchFullTextIndex() {
        fullTextIndex.searchHits = List.of(WikiSearchHit.builder()
                .path("docs/guide")
                .title("Guide")
                .excerpt("Guide body")
                .kind(WikiNodeKind.PAGE)
                .build());

        List<WikiSearchHit> hits = service.search("space-1", "guide");

        assertEquals(1, hits.size());
        assertEquals("docs/guide", hits.getFirst().getPath());
    }

    @Test
    void shouldNotSynchronizeSpaceOnFullTextSearch() {
        documentCatalog.documents = List.of(document("docs/guide", "guide-revision"));
        fullTextIndex.indexedRevisions = Map.of();
        fullTextIndex.searchHits = List.of(WikiSearchHit.builder()
                .path("docs/guide")
                .title("Guide")
                .kind(WikiNodeKind.PAGE)
                .build());

        service.search("space-1", "guide");

        // search must not trigger indexing side-effects on the hot path
        assertEquals(0, fullTextIndex.applyChangesCount);
        assertEquals(0, indexingExecutor.submittedCount);
    }

    @Test
    void shouldNotSynchronizeSpaceOnSemanticSearch() {
        documentCatalog.documents = List.of(document("docs/guide", "guide-revision"));
        fullTextIndex.indexedRevisions = Map.of();
        llmSettingsRepository.settings = embeddingSettings();

        service.semanticSearch("space-1", "guide");

        assertEquals(0, fullTextIndex.applyChangesCount);
        assertEquals(0, embeddingIndex.applyChangesCount);
        assertEquals(0, indexingExecutor.submittedCount);
    }

    @Test
    void shouldNotSynchronizeSpaceOnEmbeddingSearch() {
        llmSettingsRepository.settings = embeddingSettings();

        service.embeddingSearch("space-1", "guide");

        assertEquals(0, fullTextIndex.applyChangesCount);
        assertEquals(0, embeddingIndex.applyChangesCount);
        assertEquals(0, indexingExecutor.submittedCount);
    }

    @Test
    void shouldNotSynchronizeSpaceOnGetStatus() {
        documentCatalog.documents = List.of(document("docs/guide", "guide-revision"));
        fullTextIndex.indexedRevisions = Map.of();

        service.getStatus("space-1");

        assertEquals(0, fullTextIndex.applyChangesCount);
        assertEquals(0, indexingExecutor.submittedCount);
    }

    @Test
    void shouldRecordUpsertsSynchronouslyOnTheWritePath() {
        // recordUpsert is invoked from the write path where callers already
        // expect to pay for indexing cost. Only full space reconciliation is
        // moved to the background executor.
        service.recordUpsert("space-1", document("docs/guide", "guide-revision"));

        assertEquals(0, indexingExecutor.submittedCount);
        assertEquals(List.of("docs/guide"), fullTextIndex.upsertedPaths);
    }

    @Test
    void shouldSwallowBackgroundSynchronizationFailures() {
        // Background indexing must never leak exceptions into the executor thread —
        // otherwise a single bad reconciliation can kill the single-thread pool and
        // block all subsequent scheduled passes.
        fullTextIndex.failOnApply = true;
        documentCatalog.documents = List.of(document("docs/guide", "guide-revision"));
        fullTextIndex.indexedRevisions = Map.of();

        service.scheduleSynchronize("space-1");

        assertEquals(1, indexingExecutor.submittedCount);
        assertEquals(0, indexingExecutor.uncaughtFailures);
    }

    @Test
    void shouldDispatchScheduledSynchronizeThroughIndexingExecutor() {
        documentCatalog.documents = List.of(document("docs/guide", "guide-revision"));
        fullTextIndex.indexedRevisions = Map.of();

        service.scheduleSynchronize("space-1");

        assertEquals(1, indexingExecutor.submittedCount);
        assertEquals(List.of("docs/guide"), fullTextIndex.upsertedPaths);
    }

    @Test
    void shouldReturnEmptySemanticSearchWithoutEmbeddingModel() {
        List<WikiEmbeddingSearchHit> hits = service.embeddingSearch("space-1", "guide");

        assertTrue(hits.isEmpty());
        assertEquals(0, embeddingPort.callCount);
    }

    @Test
    void shouldRespectCallerLimitOnSemanticSearch() {
        llmSettingsRepository.settings = embeddingSettings();
        embeddingIndex.searchHits = List.of(
                WikiEmbeddingSearchHit.builder().id("a").path("docs/a").title("A").kind(WikiNodeKind.PAGE).build(),
                WikiEmbeddingSearchHit.builder().id("b").path("docs/b").title("B").kind(WikiNodeKind.PAGE).build(),
                WikiEmbeddingSearchHit.builder().id("c").path("docs/c").title("C").kind(WikiNodeKind.PAGE).build());

        WikiSemanticSearchResult result = service.semanticSearch("space-1", "alpha", 2);

        assertEquals(2, result.getSemanticHits().size(),
                "caller limit should bound the returned list, got " + result.getSemanticHits().size());
    }

    @Test
    void shouldFuseDenseAndLexicalHitsViaReciprocalRankFusion() {
        // Hybrid retrieval: a path that appears in both dense and lexical
        // results should outrank a path that appears in only one index,
        // regardless of raw scores — RRF is scale-free and that's the point.
        llmSettingsRepository.settings = embeddingSettings();
        embeddingIndex.searchHits = List.of(
                WikiEmbeddingSearchHit.builder().id("a").path("docs/a").title("A").kind(WikiNodeKind.PAGE)
                        .score(0.9).excerpt("alpha").build(),
                WikiEmbeddingSearchHit.builder().id("b").path("docs/b").title("B").kind(WikiNodeKind.PAGE)
                        .score(0.1).excerpt("beta").build());
        fullTextIndex.searchHits = List.of(
                WikiSearchHit.builder().path("docs/c").title("C").kind(WikiNodeKind.PAGE).excerpt("gamma").build(),
                WikiSearchHit.builder().path("docs/a").title("A").kind(WikiNodeKind.PAGE).excerpt("alpha lex").build());

        WikiSemanticSearchResult result = service.semanticSearch("space-1", "alpha");

        assertEquals("hybrid", result.getMode());
        assertTrue(result.isSemanticReady());
        List<String> fusedPaths = result.getSemanticHits().stream()
                .map(WikiEmbeddingSearchHit::getPath)
                .toList();
        assertEquals("docs/a", fusedPaths.getFirst(),
                "doc in both indexes should rank first, got " + fusedPaths);
        assertTrue(fusedPaths.contains("docs/b"));
        assertTrue(fusedPaths.contains("docs/c"));
    }

    @Test
    void shouldFallbackToFullTextWhenSemanticSearchIsNotConfigured() {
        fullTextIndex.searchHits = List.of(WikiSearchHit.builder()
                .path("docs/guide")
                .title("Guide")
                .excerpt("Guide body")
                .kind(WikiNodeKind.PAGE)
                .build());

        WikiSemanticSearchResult result = service.semanticSearch("space-1", "guide");

        assertEquals("lexical-fallback", result.getMode());
        assertFalse(result.isSemanticReady());
        assertEquals("embedding-model-not-configured", result.getFallbackReason());
        assertEquals("docs/guide", result.getFallbackHits().getFirst().getPath());
    }

    @Test
    void shouldReportIndexStatus() {
        documentCatalog.documents = List.of(document("root", "root-revision"));
        fullTextIndex.indexedRevisions = Map.of("root", "root-revision");

        WikiIndexStatus status = service.getStatus("space-1");

        assertTrue(status.isReady());
        assertFalse(status.isEmbeddingsReady());
        assertEquals(1, status.getFullTextIndexedDocuments());
        assertEquals(0, status.getStaleDocuments());
    }

    private LlmSettings embeddingSettings() {
        return LlmSettings.builder()
                .providers(Map.of("openai", LlmProviderConfig.builder()
                        .apiKey(Secret.of("secret"))
                        .apiType(LlmApiType.OPENAI)
                        .build()))
                .models(List.of(LlmModelConfig.builder()
                        .id("embedding-model")
                        .provider("openai")
                        .modelId("text-embedding-3-small")
                        .kind(LlmModelKind.EMBEDDING)
                        .enabled(true)
                        .build()))
                .build();
    }

    private WikiIndexedDocument document(String path, String revision) {
        return WikiIndexedDocument.builder()
                .id(path)
                .path(path)
                .parentPath(path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : null)
                .title(path.isBlank() ? "Root" : path)
                .body("body for " + path)
                .kind(path.isBlank() ? WikiNodeKind.ROOT : WikiNodeKind.PAGE)
                .updatedAt(Instant.parse("2026-04-11T00:00:00Z"))
                .revision(revision)
                .build();
    }

    private static class InMemoryDocumentCatalog implements WikiDocumentCatalogPort {
        private List<WikiIndexedDocument> documents = List.of();

        @Override
        public List<WikiIndexedDocument> listDocuments(String spaceId) {
            return documents;
        }
    }

    private static class InMemoryFullTextIndex implements WikiFullTextIndexPort {
        private Map<String, String> indexedRevisions = Map.of();
        private List<WikiSearchHit> searchHits = List.of();
        private List<String> upsertedPaths = List.of();
        private List<String> deletedPaths = List.of();
        private int applyChangesCount;
        private boolean failOnApply;

        @Override
        public void applyChanges(String spaceId, WikiDocumentChangeSet changeSet) {
            applyChangesCount++;
            if (failOnApply) {
                throw new IllegalStateException("simulated failure");
            }
            upsertedPaths = changeSet.getUpserts().stream().map(WikiIndexedDocument::getPath).toList();
            deletedPaths = changeSet.getDeletedPaths();
            java.util.Map<String, String> merged = new java.util.LinkedHashMap<>(indexedRevisions);
            changeSet.getUpserts().forEach(document -> merged.put(document.getPath(), document.getRevision()));
            changeSet.getDeletedPaths().forEach(merged::remove);
            indexedRevisions = merged;
        }

        @Override
        public List<WikiSearchHit> search(String spaceId, String query, int limit) {
            return searchHits;
        }

        @Override
        public List<String> listIndexedPaths(String spaceId) {
            return indexedRevisions.keySet().stream().toList();
        }

        @Override
        public Map<String, String> listIndexedRevisions(String spaceId) {
            return indexedRevisions;
        }

        @Override
        public int count(String spaceId) {
            return indexedRevisions.size();
        }
    }

    private static class InMemoryEmbeddingIndex implements WikiEmbeddingIndexPort {
        private List<String> upsertedPaths = List.of();
        private List<WikiEmbeddingDocument> lastEmbeddingUpserts = List.of();
        private List<WikiEmbeddingSearchHit> searchHits = List.of();
        private Map<String, String> indexedRevisions = Map.of();
        private String storedEmbeddingModelId;
        private int applyChangesCount;

        @Override
        public void applyChanges(String spaceId, WikiDocumentChangeSet changeSet) {
            applyChangesCount++;
            lastEmbeddingUpserts = changeSet.getEmbeddingUpserts();
            upsertedPaths = changeSet.getEmbeddingUpserts().stream()
                    .map(WikiEmbeddingDocument::getDocument)
                    .map(WikiIndexedDocument::getPath)
                    .distinct()
                    .toList();
            indexedRevisions = changeSet.getEmbeddingUpserts().stream()
                    .map(WikiEmbeddingDocument::getDocument)
                    .collect(java.util.stream.Collectors.toMap(WikiIndexedDocument::getPath,
                            WikiIndexedDocument::getRevision, (first, second) -> first));
            changeSet.getEmbeddingUpserts().stream()
                    .map(WikiEmbeddingDocument::getEmbeddingModelId)
                    .filter(id -> id != null && !id.isBlank())
                    .findFirst()
                    .ifPresent(id -> storedEmbeddingModelId = id);
        }

        @Override
        public List<WikiEmbeddingSearchHit> search(String spaceId, List<Double> embedding, int limit) {
            return searchHits;
        }

        @Override
        public List<String> listIndexedPaths(String spaceId) {
            return upsertedPaths;
        }

        @Override
        public Map<String, String> listIndexedRevisions(String spaceId) {
            return indexedRevisions;
        }

        @Override
        public int count(String spaceId) {
            return upsertedPaths.size();
        }

        @Override
        public Optional<String> findStoredEmbeddingModelId(String spaceId) {
            return Optional.ofNullable(storedEmbeddingModelId);
        }
    }

    private static class InMemoryLlmSettingsRepository implements LlmSettingsRepository {
        private LlmSettings settings = LlmSettings.builder().build();

        @Override
        public void initialize() {
        }

        @Override
        public LlmSettings load() {
            return settings;
        }

        @Override
        public LlmSettings save(LlmSettings settings) {
            this.settings = settings;
            return settings;
        }
    }

    private static class CountingEmbeddingPort implements LlmEmbeddingPort {
        private int callCount;
        private List<String> lastInputs = List.of();

        @Override
        public LlmEmbeddingResponse embed(LlmEmbeddingRequest request) {
            callCount++;
            lastInputs = List.copyOf(request.getInputs());
            return LlmEmbeddingResponse.builder()
                    .embeddings(request.getInputs().stream().map(input -> List.of(1.0d, 0.0d)).toList())
                    .build();
        }
    }

    private static class CountingExecutor implements Executor {
        private int submittedCount;
        private int uncaughtFailures;

        @Override
        public void execute(Runnable command) {
            submittedCount++;
            try {
                command.run();
            } catch (RuntimeException exception) {
                uncaughtFailures++;
            }
        }
    }
}
