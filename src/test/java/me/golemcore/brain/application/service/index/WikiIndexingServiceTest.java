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
import me.golemcore.brain.domain.WikiSearchMode;
import me.golemcore.brain.domain.WikiSearchResult;
import me.golemcore.brain.domain.WikiSearchResultHit;
import me.golemcore.brain.domain.llm.LlmApiType;
import me.golemcore.brain.domain.llm.LlmEmbeddingRequest;
import me.golemcore.brain.domain.llm.LlmEmbeddingResponse;
import me.golemcore.brain.domain.llm.LlmModelConfig;
import me.golemcore.brain.domain.llm.LlmModelKind;
import me.golemcore.brain.domain.llm.LlmProviderConfig;
import me.golemcore.brain.domain.llm.LlmSettings;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void shouldFallbackToFtsWhenStoredModelDiffersOnHybridSearch() {
        llmSettingsRepository.settings = embeddingSettings();
        embeddingIndex.upsertedPaths = List.of("docs/guide");
        embeddingIndex.storedEmbeddingModelId = "old-embedding-model";
        embeddingIndex.searchHits = List.of(WikiEmbeddingSearchHit.builder()
                .id("dense")
                .path("docs/dense")
                .title("Dense")
                .kind(WikiNodeKind.PAGE)
                .build());
        fullTextIndex.searchHits = List.of(WikiSearchHit.builder()
                .path("docs/guide")
                .title("Guide")
                .kind(WikiNodeKind.PAGE)
                .build());

        WikiSearchResult result = service.search("space-1", "guide", WikiSearchMode.HYBRID, 10);

        assertEquals("fts-fallback", result.getMode());
        assertFalse(result.isSemanticReady());
        assertEquals("embedding-model-mismatch", result.getFallbackReason());
        assertEquals("docs/guide", result.getHits().getFirst().getPath());
        assertEquals(0, embeddingPort.callCount, "stale embedding index should be rejected before embedding the query");
        assertEquals(0, embeddingIndex.searchCount, "stale vectors must not be searched with the new model");
    }

    @Test
    void shouldFallbackToFtsWhenEmbeddingIndexContainsMixedStoredModels() {
        llmSettingsRepository.settings = embeddingSettings();
        fullTextIndex.indexedRevisions = Map.of(
                "docs/current", "rev-current",
                "docs/old", "rev-old");
        embeddingIndex.indexedRevisions = fullTextIndex.indexedRevisions;
        embeddingIndex.upsertedPaths = List.of("docs/current", "docs/old");
        embeddingIndex.storedEmbeddingModelIds = List.of("embedding-model", "old-embedding-model");
        embeddingIndex.searchHits = List.of(WikiEmbeddingSearchHit.builder()
                .id("dense")
                .path("docs/old")
                .title("Old")
                .kind(WikiNodeKind.PAGE)
                .build());
        fullTextIndex.searchHits = List.of(WikiSearchHit.builder()
                .path("docs/current")
                .title("Current")
                .kind(WikiNodeKind.PAGE)
                .build());

        WikiSearchResult result = service.search("space-1", "current", WikiSearchMode.HYBRID, 10);

        assertEquals("fts-fallback", result.getMode());
        assertFalse(result.isSemanticReady());
        assertEquals("embedding-model-mismatch", result.getFallbackReason());
        assertEquals("docs/current", result.getHits().getFirst().getPath());
        assertEquals(0, embeddingPort.callCount,
                "mixed-model embedding index should be rejected before embedding query");
        assertEquals(0, embeddingIndex.searchCount, "mixed-model vectors must not be searched");
    }

    @Test
    void shouldReturnEmptyEmbeddingSearchWhenStoredModelDiffers() {
        llmSettingsRepository.settings = embeddingSettings();
        embeddingIndex.upsertedPaths = List.of("docs/guide");
        embeddingIndex.storedEmbeddingModelId = "old-embedding-model";
        embeddingIndex.searchHits = List.of(WikiEmbeddingSearchHit.builder()
                .id("dense")
                .path("docs/dense")
                .title("Dense")
                .kind(WikiNodeKind.PAGE)
                .build());

        List<WikiEmbeddingSearchHit> hits = service.embeddingSearch("space-1", "guide");

        assertTrue(hits.isEmpty());
        assertEquals(0, embeddingPort.callCount, "stale embedding index should be rejected before embedding the query");
        assertEquals(0, embeddingIndex.searchCount, "stale vectors must not be searched with the new model");
    }

    @Test
    void shouldNotScanIndexedRevisionsOnHybridSearchHotPath() {
        llmSettingsRepository.settings = embeddingSettings();
        markIndexesReady(Map.of(
                "docs/a", "rev-a",
                "docs/b", "rev-b"));
        embeddingIndex.searchHits = List.of(
                WikiEmbeddingSearchHit.builder().id("a").path("docs/a").title("A").kind(WikiNodeKind.PAGE).build());
        fullTextIndex.searchHits = List.of(
                WikiSearchHit.builder().path("docs/b").title("B").kind(WikiNodeKind.PAGE).build());

        WikiSearchResult result = service.search("space-1", "alpha", WikiSearchMode.HYBRID, 10);

        assertEquals("hybrid", result.getMode());
        assertEquals(0, fullTextIndex.listIndexedRevisionsCount,
                "hybrid search must not scan every full-text revision on the hot path");
        assertEquals(0, embeddingIndex.listIndexedRevisionsCount,
                "hybrid search must not scan every embedding revision on the hot path");
    }

    @Test
    void shouldNotWriteNewModelEmbeddingsOnIncrementalUpsertWhenStoredModelIsStale() {
        llmSettingsRepository.settings = embeddingSettings();
        fullTextIndex.indexedRevisions = Map.of("docs/old", "rev-old");
        embeddingIndex.indexedRevisions = Map.of("docs/old", "rev-old");
        embeddingIndex.upsertedPaths = List.of("docs/old");
        embeddingIndex.storedEmbeddingModelId = "old-embedding-model";
        fullTextIndex.searchHits = List.of(WikiSearchHit.builder()
                .path("docs/edited")
                .title("Edited")
                .kind(WikiNodeKind.PAGE)
                .build());

        service.recordUpsert("space-1", document("docs/edited", "rev-edited"));
        WikiSearchResult result = service.search("space-1", "edited", WikiSearchMode.HYBRID, 10);

        assertEquals(List.of("docs/edited"), fullTextIndex.upsertedPaths);
        assertEquals(0, embeddingPort.callCount,
                "incremental upsert must not add current-model vectors into a stale old-model index");
        assertTrue(embeddingIndex.lastEmbeddingUpserts.isEmpty());
        assertEquals("old-embedding-model", embeddingIndex.storedEmbeddingModelId);
        assertEquals("fts-fallback", result.getMode());
        assertEquals("embedding-model-mismatch", result.getFallbackReason());
        assertEquals(0, embeddingIndex.searchCount, "mixed-model vectors must not be searched");
    }

    @Test
    void shouldFallbackToFtsWhenEmbeddingIndexIsIncompleteOnHybridSearch() {
        llmSettingsRepository.settings = embeddingSettings();
        fullTextIndex.indexedRevisions = Map.of(
                "docs/one", "rev-one",
                "docs/two", "rev-two");
        embeddingIndex.indexedRevisions = Map.of("docs/one", "rev-one");
        embeddingIndex.upsertedPaths = List.of("docs/one");
        embeddingIndex.storedEmbeddingModelId = "embedding-model";
        embeddingIndex.searchHits = List.of(WikiEmbeddingSearchHit.builder()
                .id("dense")
                .path("docs/one")
                .title("One")
                .kind(WikiNodeKind.PAGE)
                .build());
        fullTextIndex.searchHits = List.of(WikiSearchHit.builder()
                .path("docs/two")
                .title("Two")
                .kind(WikiNodeKind.PAGE)
                .build());

        WikiSearchResult result = service.search("space-1", "two", WikiSearchMode.HYBRID, 10);

        assertEquals("fts-fallback", result.getMode());
        assertFalse(result.isSemanticReady());
        assertEquals("embedding-index-incomplete", result.getFallbackReason());
        assertEquals("docs/two", result.getHits().getFirst().getPath());
        assertEquals(0, embeddingPort.callCount,
                "incomplete embedding index should be rejected before embedding query");
        assertEquals(0, embeddingIndex.searchCount, "partial vectors must not be searched as vector-ready");
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
    void shouldReportEmbeddingsNotReadyWhenOnlySomeDocumentsHaveVectors() {
        documentCatalog.documents = List.of(
                document("docs/one", "rev-one"),
                document("docs/two", "rev-two"));
        fullTextIndex.indexedRevisions = Map.of();
        llmSettingsRepository.settings = embeddingSettings();
        embeddingPort.maxEmbeddings = 1;

        service.synchronizeSpace("space-1");

        assertEquals(List.of("docs/one"), embeddingIndex.upsertedPaths);
        assertFalse(service.getStatus("space-1").isEmbeddingsReady(),
                "a partial embedding response must not mark the whole space ready");
    }

    @Test
    void shouldDeleteEmbeddingPathsMissingFromCatalogWhenFullTextIsAlreadyClean() {
        documentCatalog.documents = List.of(document("docs/keep", "rev-keep"));
        fullTextIndex.indexedRevisions = Map.of("docs/keep", "rev-keep");
        embeddingIndex.indexedRevisions = Map.of(
                "docs/keep", "rev-keep",
                "docs/stale", "rev-stale");
        embeddingIndex.upsertedPaths = List.of("docs/keep", "docs/stale");
        embeddingIndex.storedEmbeddingModelId = "embedding-model";
        llmSettingsRepository.settings = embeddingSettings();

        service.synchronizeSpace("space-1");

        assertEquals(List.of("docs/stale"), embeddingIndex.deletedPaths);
        assertEquals(Map.of("docs/keep", "rev-keep"), embeddingIndex.indexedRevisions);
    }

    @Test
    void shouldFullRebuildStaleEmbeddingIndexEvenWhenCatalogIsEmpty() {
        documentCatalog.documents = List.of();
        fullTextIndex.indexedRevisions = Map.of();
        embeddingIndex.indexedRevisions = Map.of("docs/stale", "rev-stale");
        embeddingIndex.upsertedPaths = List.of("docs/stale");
        embeddingIndex.storedEmbeddingModelId = "old-embedding-model";
        llmSettingsRepository.settings = embeddingSettings();

        service.synchronizeSpace("space-1");

        assertEquals(1, embeddingIndex.fullRebuildCount);
        assertTrue(embeddingIndex.indexedRevisions.isEmpty());
    }

    @Test
    void shouldEmbedDocumentsThatOnlyHaveATitle() {
        documentCatalog.documents = List.of(WikiIndexedDocument.builder()
                .id("docs/title-only")
                .path("docs/title-only")
                .parentPath("docs")
                .title("Title Only")
                .body("")
                .kind(WikiNodeKind.PAGE)
                .updatedAt(Instant.parse("2026-04-11T00:00:00Z"))
                .revision("title-revision")
                .build());
        fullTextIndex.indexedRevisions = Map.of();
        llmSettingsRepository.settings = embeddingSettings();

        service.synchronizeSpace("space-1");

        assertEquals(1, embeddingPort.callCount);
        assertEquals(List.of("Title Only"), embeddingPort.lastInputs);
        assertEquals(List.of("docs/title-only"), embeddingIndex.upsertedPaths);
    }

    @Test
    void shouldKeepLastIndexingErrorWhenEmbeddingProviderFailsDuringSynchronization() {
        documentCatalog.documents = List.of(document("docs/guide", "guide-revision"));
        fullTextIndex.indexedRevisions = Map.of();
        llmSettingsRepository.settings = embeddingSettings();
        embeddingPort.failOnEmbed = true;

        service.synchronizeSpace("space-1");

        WikiIndexStatus status = service.getStatus("space-1");
        assertEquals("simulated embedding failure", status.getLastIndexingError());
        assertFalse(status.isEmbeddingsReady());
        assertEquals(List.of("docs/guide"), fullTextIndex.upsertedPaths);
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
    void shouldNotSynchronizeSpaceOnHybridSearch() {
        documentCatalog.documents = List.of(document("docs/guide", "guide-revision"));
        fullTextIndex.indexedRevisions = Map.of();
        llmSettingsRepository.settings = embeddingSettings();

        service.search("space-1", "guide", WikiSearchMode.HYBRID, 10);

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
    void shouldDispatchScheduledRebuildThroughIndexingExecutor() {
        documentCatalog.documents = List.of(document("docs/guide", "guide-revision"));

        service.scheduleRebuild("space-1");

        assertEquals(1, indexingExecutor.submittedCount);
        assertEquals(List.of("docs/guide"), fullTextIndex.upsertedPaths);
        assertEquals(0, indexingExecutor.uncaughtFailures);
        assertTrue(service.getStatus("space-1").getLastFullRebuildAt() != null);
    }

    @Test
    void shouldCoalesceDuplicateScheduledRebuildsPerSpaceWhilePending() {
        CountingExecutor queuedExecutor = new CountingExecutor(false);
        WikiIndexingService queuedService = new WikiIndexingService(
                documentCatalog,
                fullTextIndex,
                embeddingIndex,
                llmSettingsRepository,
                embeddingPort,
                queuedExecutor);
        documentCatalog.documents = List.of(document("docs/guide", "guide-revision"));

        queuedService.scheduleRebuild("space-1");
        queuedService.scheduleRebuild("space-1");
        queuedService.scheduleRebuild("space-2");

        assertEquals(2, queuedExecutor.submittedCount);
        assertEquals(0, fullTextIndex.applyChangesCount);

        queuedExecutor.runNext();

        assertEquals(2, queuedExecutor.submittedCount);

        queuedService.scheduleRebuild("space-1");

        assertEquals(3, queuedExecutor.submittedCount);
    }

    @Test
    void shouldRunOneFollowUpRebuildWhenRequestArrivesDuringRunningRebuild() {
        CountingExecutor queuedExecutor = new CountingExecutor(false);
        WikiIndexingService queuedService = new WikiIndexingService(
                documentCatalog,
                fullTextIndex,
                embeddingIndex,
                llmSettingsRepository,
                embeddingPort,
                queuedExecutor);
        documentCatalog.documents = List.of(document("docs/guide", "guide-revision"));
        AtomicBoolean requestedWhileRunning = new AtomicBoolean();
        documentCatalog.beforeList = () -> {
            if (requestedWhileRunning.compareAndSet(false, true)) {
                queuedService.scheduleRebuild("space-1");
                queuedService.scheduleRebuild("space-1");
            }
        };

        queuedService.scheduleRebuild("space-1");

        assertEquals(1, queuedExecutor.submittedCount);

        queuedExecutor.runNext();

        assertEquals(2, queuedExecutor.submittedCount);
        assertEquals(1, fullTextIndex.applyChangesCount);

        queuedExecutor.runNext();

        assertEquals(2, queuedExecutor.submittedCount);
        assertEquals(2, fullTextIndex.applyChangesCount);
    }

    @Test
    void shouldReleaseScheduledRebuildStateWhenExecutorRejectsTask() {
        RejectingExecutor rejectingExecutor = new RejectingExecutor();
        WikiIndexingService rejectingService = new WikiIndexingService(
                documentCatalog,
                fullTextIndex,
                embeddingIndex,
                llmSettingsRepository,
                embeddingPort,
                rejectingExecutor);
        documentCatalog.documents = List.of(document("docs/guide", "guide-revision"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> rejectingService.scheduleRebuild("space-1"));

        assertEquals("executor rejected task", thrown.getMessage());

        rejectingExecutor.reject = false;
        rejectingService.scheduleRebuild("space-1");

        assertEquals(2, rejectingExecutor.submittedCount);
        assertEquals(1, fullTextIndex.applyChangesCount);
    }

    @Test
    void shouldPreserveLastFullRebuildTimestampAfterIncrementalSuccess() {
        documentCatalog.documents = List.of(document("docs/guide", "guide-revision"));

        service.rebuildSpace("space-1");
        Instant rebuiltAt = service.getStatus("space-1").getLastFullRebuildAt();
        service.recordUpsert("space-1", document("docs/edited", "edited-revision"));

        assertEquals(rebuiltAt, service.getStatus("space-1").getLastFullRebuildAt());
    }

    @Test
    void shouldReturnEmptyEmbeddingSearchWithoutEmbeddingModel() {
        List<WikiEmbeddingSearchHit> hits = service.embeddingSearch("space-1", "guide");

        assertTrue(hits.isEmpty());
        assertEquals(0, embeddingPort.callCount);
    }

    @Test
    void shouldRespectCallerLimitOnHybridSearch() {
        llmSettingsRepository.settings = embeddingSettings();
        markIndexesReady(Map.of(
                "docs/a", "rev-a",
                "docs/b", "rev-b",
                "docs/c", "rev-c"));
        embeddingIndex.searchHits = List.of(
                WikiEmbeddingSearchHit.builder().id("a").path("docs/a").title("A").kind(WikiNodeKind.PAGE).build(),
                WikiEmbeddingSearchHit.builder().id("b").path("docs/b").title("B").kind(WikiNodeKind.PAGE).build(),
                WikiEmbeddingSearchHit.builder().id("c").path("docs/c").title("C").kind(WikiNodeKind.PAGE).build());

        WikiSearchResult result = service.search("space-1", "alpha", WikiSearchMode.HYBRID, 2);

        assertEquals(2, result.getHits().size(),
                "caller limit should bound the returned list, got " + result.getHits().size());
    }

    @Test
    void shouldRespectCallerLimitOnHybridFallbackSearch() {
        fullTextIndex.searchHits = List.of(
                WikiSearchHit.builder().path("docs/a").title("A").kind(WikiNodeKind.PAGE).build(),
                WikiSearchHit.builder().path("docs/b").title("B").kind(WikiNodeKind.PAGE).build(),
                WikiSearchHit.builder().path("docs/c").title("C").kind(WikiNodeKind.PAGE).build());

        WikiSearchResult result = service.search("space-1", "alpha", WikiSearchMode.HYBRID, 2);

        assertEquals("fts-fallback", result.getMode());
        assertEquals(2, result.getHits().size(),
                "caller limit should bound fallback hits, got " + result.getHits().size());
    }

    @Test
    void shouldFuseDenseAndFtsHitsViaReciprocalRankFusion() {
        // Hybrid retrieval: a path that appears in both dense and FTS
        // results should outrank a path that appears in only one index,
        // regardless of raw scores — RRF is scale-free and that's the point.
        llmSettingsRepository.settings = embeddingSettings();
        markIndexesReady(Map.of(
                "docs/a", "rev-a",
                "docs/b", "rev-b",
                "docs/c", "rev-c"));
        embeddingIndex.searchHits = List.of(
                WikiEmbeddingSearchHit.builder().id("a").path("docs/a").title("A").kind(WikiNodeKind.PAGE)
                        .score(0.9).excerpt("alpha").build(),
                WikiEmbeddingSearchHit.builder().id("b").path("docs/b").title("B").kind(WikiNodeKind.PAGE)
                        .score(0.1).excerpt("beta").build());
        fullTextIndex.searchHits = List.of(
                WikiSearchHit.builder().path("docs/c").title("C").kind(WikiNodeKind.PAGE).excerpt("gamma").build(),
                WikiSearchHit.builder().path("docs/a").title("A").kind(WikiNodeKind.PAGE).excerpt("alpha lex").build());

        WikiSearchResult result = service.search("space-1", "alpha", WikiSearchMode.HYBRID, 10);

        assertEquals("hybrid", result.getMode());
        assertTrue(result.isSemanticReady());
        List<String> fusedPaths = result.getHits().stream()
                .map(WikiSearchResultHit::getPath)
                .toList();
        assertEquals("docs/a", fusedPaths.getFirst(),
                "doc in both indexes should rank first, got " + fusedPaths);
        assertTrue(fusedPaths.contains("docs/b"));
        assertTrue(fusedPaths.contains("docs/c"));
    }

    @Test
    void shouldFallbackToFullTextWhenHybridSearchIsNotConfigured() {
        fullTextIndex.searchHits = List.of(WikiSearchHit.builder()
                .path("docs/guide")
                .title("Guide")
                .excerpt("Guide body")
                .kind(WikiNodeKind.PAGE)
                .build());

        WikiSearchResult result = service.search("space-1", "guide", WikiSearchMode.HYBRID, 10);

        assertEquals("fts-fallback", result.getMode());
        assertFalse(result.isSemanticReady());
        assertEquals("embedding-model-not-configured", result.getFallbackReason());
        assertEquals("docs/guide", result.getHits().getFirst().getPath());
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

    @Test
    void shouldReportIndexNotReadyWhenFullTextRevisionDiffersFromCatalog() {
        documentCatalog.documents = List.of(document("docs/guide", "new-revision"));
        fullTextIndex.indexedRevisions = Map.of("docs/guide", "old-revision");

        WikiIndexStatus status = service.getStatus("space-1");

        assertFalse(status.isReady());
        assertEquals(1, status.getStaleDocuments());
    }

    @Test
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    void shouldUseConcurrentObservationStoreForBackgroundIndexing() throws Exception {
        java.lang.reflect.Field field = WikiIndexingService.class.getDeclaredField("observationsBySpaceId");
        field.setAccessible(true);

        assertTrue(field.get(service) instanceof ConcurrentMap,
                "background reconciliation and request paths can update observations concurrently");

        Class<?> observationClass = null;
        for (Class<?> candidate : WikiIndexingService.class.getDeclaredClasses()) {
            if ("IndexingObservation".equals(candidate.getSimpleName())) {
                observationClass = candidate;
                break;
            }
        }
        assertTrue(observationClass != null);
        assertTrue(java.lang.reflect.Modifier.isVolatile(
                observationClass.getDeclaredField("lastIndexingError").getModifiers()));
        assertTrue(java.lang.reflect.Modifier.isVolatile(
                observationClass.getDeclaredField("lastFullRebuildAt").getModifiers()));
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

    private void markIndexesReady(Map<String, String> revisions) {
        fullTextIndex.indexedRevisions = revisions;
        embeddingIndex.indexedRevisions = revisions;
        embeddingIndex.upsertedPaths = revisions.keySet().stream().toList();
        embeddingIndex.storedEmbeddingModelId = "embedding-model";
    }

    private static class InMemoryDocumentCatalog implements WikiDocumentCatalogPort {
        private List<WikiIndexedDocument> documents = List.of();
        private Runnable beforeList = () -> {
        };

        @Override
        public List<WikiIndexedDocument> listDocuments(String spaceId) {
            beforeList.run();
            return documents;
        }
    }

    private static class InMemoryFullTextIndex implements WikiFullTextIndexPort {
        private Map<String, String> indexedRevisions = Map.of();
        private List<WikiSearchHit> searchHits = List.of();
        private List<String> upsertedPaths = List.of();
        private List<String> deletedPaths = List.of();
        private int applyChangesCount;
        private int listIndexedRevisionsCount;
        private boolean failOnApply;

        @Override
        public void applyChanges(String spaceId, WikiDocumentChangeSet changeSet) {
            applyChangesCount++;
            if (failOnApply) {
                throw new IllegalStateException("simulated failure");
            }
            upsertedPaths = changeSet.getUpserts().stream().map(WikiIndexedDocument::getPath).toList();
            deletedPaths = changeSet.getDeletedPaths();
            Map<String, String> merged = new LinkedHashMap<>(indexedRevisions);
            changeSet.getUpserts().forEach(document -> merged.put(document.getPath(), document.getRevision()));
            changeSet.getDeletedPaths().forEach(merged::remove);
            indexedRevisions = merged;
        }

        @Override
        public List<WikiSearchHit> search(String spaceId, String query, int limit) {
            return searchHits.stream().limit(Math.max(1, limit)).toList();
        }

        @Override
        public List<String> listIndexedPaths(String spaceId) {
            return indexedRevisions.keySet().stream().toList();
        }

        @Override
        public Map<String, String> listIndexedRevisions(String spaceId) {
            listIndexedRevisionsCount++;
            return indexedRevisions;
        }

        @Override
        public int count(String spaceId) {
            return indexedRevisions.size();
        }
    }

    private static class InMemoryEmbeddingIndex implements WikiEmbeddingIndexPort {
        private List<String> upsertedPaths = List.of();
        private List<String> deletedPaths = List.of();
        private List<WikiEmbeddingDocument> lastEmbeddingUpserts = List.of();
        private List<WikiEmbeddingSearchHit> searchHits = List.of();
        private Map<String, String> indexedRevisions = Map.of();
        private String storedEmbeddingModelId;
        private List<String> storedEmbeddingModelIds = List.of();
        private int applyChangesCount;
        private int fullRebuildCount;
        private int searchCount;
        private int listIndexedRevisionsCount;

        @Override
        public void applyChanges(String spaceId, WikiDocumentChangeSet changeSet) {
            applyChangesCount++;
            if (changeSet.isFullRebuild()) {
                fullRebuildCount++;
            }
            lastEmbeddingUpserts = changeSet.getEmbeddingUpserts();
            Map<String, String> merged = changeSet.isFullRebuild()
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(indexedRevisions);
            changeSet.getDeletedPaths().forEach(merged::remove);
            Map<String, String> upsertedRevisions = changeSet.getEmbeddingUpserts().stream()
                    .map(WikiEmbeddingDocument::getDocument)
                    .collect(java.util.stream.Collectors.toMap(WikiIndexedDocument::getPath,
                            WikiIndexedDocument::getRevision, (first, second) -> first));
            merged.putAll(upsertedRevisions);
            indexedRevisions = merged;
            upsertedPaths = indexedRevisions.keySet().stream().toList();
            deletedPaths = changeSet.getDeletedPaths();
            Optional<String> embeddingModelId = changeSet.getEmbeddingUpserts().stream()
                    .map(WikiEmbeddingDocument::getEmbeddingModelId)
                    .filter(id -> id != null && !id.isBlank())
                    .findFirst();
            if (embeddingModelId.isPresent()) {
                storedEmbeddingModelId = embeddingModelId.get();
            }
        }

        @Override
        public List<WikiEmbeddingSearchHit> search(String spaceId, List<Double> embedding, int limit) {
            searchCount++;
            return searchHits;
        }

        @Override
        public List<String> listIndexedPaths(String spaceId) {
            return upsertedPaths;
        }

        @Override
        public Map<String, String> listIndexedRevisions(String spaceId) {
            listIndexedRevisionsCount++;
            return indexedRevisions;
        }

        @Override
        public int count(String spaceId) {
            return upsertedPaths.size();
        }

        @Override
        public Optional<String> findStoredEmbeddingModelId(String spaceId) {
            if (!storedEmbeddingModelIds.isEmpty()) {
                return uniqueStoredEmbeddingModelId(storedEmbeddingModelIds);
            }
            return Optional.ofNullable(storedEmbeddingModelId);
        }

        private Optional<String> uniqueStoredEmbeddingModelId(List<String> modelIds) {
            Set<String> uniqueModelIds = new LinkedHashSet<>();
            for (String modelId : modelIds) {
                if (modelId == null || modelId.isBlank()) {
                    return Optional.empty();
                }
                uniqueModelIds.add(modelId.strip());
                if (uniqueModelIds.size() > 1) {
                    return Optional.empty();
                }
            }
            return uniqueModelIds.stream().findFirst();
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
        private int maxEmbeddings = Integer.MAX_VALUE;
        private boolean failOnEmbed;

        @Override
        public LlmEmbeddingResponse embed(LlmEmbeddingRequest request) {
            callCount++;
            if (failOnEmbed) {
                throw new IllegalStateException("simulated embedding failure");
            }
            lastInputs = List.copyOf(request.getInputs());
            return LlmEmbeddingResponse.builder()
                    .embeddings(request.getInputs().stream()
                            .limit(maxEmbeddings)
                            .map(input -> List.of(1.0d, 0.0d))
                            .toList())
                    .build();
        }
    }

    private static class CountingExecutor implements Executor {
        private int submittedCount;
        private int uncaughtFailures;
        private final boolean runImmediately;
        private final Queue<Runnable> queuedCommands = new ArrayDeque<>();

        private CountingExecutor() {
            this(true);
        }

        private CountingExecutor(boolean runImmediately) {
            this.runImmediately = runImmediately;
        }

        @Override
        public void execute(Runnable command) {
            submittedCount++;
            if (!runImmediately) {
                queuedCommands.add(command);
                return;
            }
            run(command);
        }

        private void runNext() {
            Runnable command = queuedCommands.poll();
            if (command != null) {
                run(command);
            }
        }

        private void run(Runnable command) {
            try {
                command.run();
            } catch (RuntimeException exception) {
                uncaughtFailures++;
            }
        }
    }

    private static class RejectingExecutor implements Executor {
        private int submittedCount;
        private boolean reject = true;

        @Override
        public void execute(Runnable command) {
            submittedCount++;
            if (reject) {
                throw new IllegalStateException("executor rejected task");
            }
            command.run();
        }
    }
}
