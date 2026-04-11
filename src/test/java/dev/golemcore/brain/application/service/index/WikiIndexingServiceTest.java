package dev.golemcore.brain.application.service.index;

import dev.golemcore.brain.application.port.out.LlmEmbeddingPort;
import dev.golemcore.brain.application.port.out.LlmSettingsRepository;
import dev.golemcore.brain.application.port.out.WikiDocumentCatalogPort;
import dev.golemcore.brain.application.port.out.WikiEmbeddingIndexPort;
import dev.golemcore.brain.application.port.out.WikiFullTextIndexPort;
import dev.golemcore.brain.domain.Secret;
import dev.golemcore.brain.domain.WikiDocumentChangeSet;
import dev.golemcore.brain.domain.WikiEmbeddingDocument;
import dev.golemcore.brain.domain.WikiEmbeddingSearchHit;
import dev.golemcore.brain.domain.WikiIndexStatus;
import dev.golemcore.brain.domain.WikiIndexedDocument;
import dev.golemcore.brain.domain.WikiNodeKind;
import dev.golemcore.brain.domain.WikiSearchHit;
import dev.golemcore.brain.domain.llm.LlmApiType;
import dev.golemcore.brain.domain.llm.LlmEmbeddingRequest;
import dev.golemcore.brain.domain.llm.LlmEmbeddingResponse;
import dev.golemcore.brain.domain.llm.LlmModelConfig;
import dev.golemcore.brain.domain.llm.LlmModelKind;
import dev.golemcore.brain.domain.llm.LlmProviderConfig;
import dev.golemcore.brain.domain.llm.LlmSettings;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    private WikiIndexingService service;

    @BeforeEach
    void setUp() {
        documentCatalog = new InMemoryDocumentCatalog();
        fullTextIndex = new InMemoryFullTextIndex();
        embeddingIndex = new InMemoryEmbeddingIndex();
        llmSettingsRepository = new InMemoryLlmSettingsRepository();
        embeddingPort = new CountingEmbeddingPort();
        service = new WikiIndexingService(
                documentCatalog,
                fullTextIndex,
                embeddingIndex,
                llmSettingsRepository,
                embeddingPort);
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
    void shouldPopulateEmbeddingIndexWhenEmbeddingModelIsConfigured() {
        documentCatalog.documents = List.of(document("docs/guide", "guide-revision"));
        fullTextIndex.indexedRevisions = Map.of();
        llmSettingsRepository.settings = LlmSettings.builder()
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
    void shouldReturnEmptySemanticSearchWithoutEmbeddingModel() {
        List<WikiEmbeddingSearchHit> hits = service.embeddingSearch("space-1", "guide");

        assertTrue(hits.isEmpty());
        assertEquals(0, embeddingPort.callCount);
    }

    @Test
    void shouldReportIndexStatus() {
        documentCatalog.documents = List.of(document("root", "root-revision"));
        fullTextIndex.indexedRevisions = Map.of("root", "root-revision");

        WikiIndexStatus status = service.getStatus("space-1");

        assertTrue(status.isReady());
        assertFalse(status.isEmbeddingsReady());
        assertEquals(1, status.getIndexedDocuments());
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

        @Override
        public void applyChanges(WikiDocumentChangeSet changeSet) {
            upsertedPaths = changeSet.getUpserts().stream().map(WikiIndexedDocument::getPath).toList();
            deletedPaths = changeSet.getDeletedPaths();
            indexedRevisions = changeSet.getUpserts().stream()
                    .collect(java.util.stream.Collectors.toMap(WikiIndexedDocument::getPath,
                            WikiIndexedDocument::getRevision));
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
        private Map<String, String> indexedRevisions = Map.of();

        @Override
        public void applyChanges(WikiDocumentChangeSet changeSet) {
            upsertedPaths = changeSet.getEmbeddingUpserts().stream()
                    .map(WikiEmbeddingDocument::getDocument)
                    .map(WikiIndexedDocument::getPath)
                    .toList();
            indexedRevisions = changeSet.getEmbeddingUpserts().stream()
                    .map(WikiEmbeddingDocument::getDocument)
                    .collect(java.util.stream.Collectors.toMap(WikiIndexedDocument::getPath,
                            WikiIndexedDocument::getRevision));
        }

        @Override
        public List<WikiEmbeddingSearchHit> search(String spaceId, List<Double> embedding, int limit) {
            return List.of();
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

        @Override
        public LlmEmbeddingResponse embed(LlmEmbeddingRequest request) {
            callCount++;
            return LlmEmbeddingResponse.builder()
                    .embeddings(request.getInputs().stream().map(input -> List.of(1.0d, 0.0d)).toList())
                    .build();
        }
    }
}
