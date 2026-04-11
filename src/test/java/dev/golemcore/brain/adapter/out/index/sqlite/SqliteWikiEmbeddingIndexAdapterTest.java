package dev.golemcore.brain.adapter.out.index.sqlite;

import dev.golemcore.brain.config.WikiProperties;
import dev.golemcore.brain.domain.WikiDocumentChangeSet;
import dev.golemcore.brain.domain.WikiEmbeddingDocument;
import dev.golemcore.brain.domain.WikiEmbeddingSearchHit;
import dev.golemcore.brain.domain.WikiIndexedDocument;
import dev.golemcore.brain.domain.WikiNodeKind;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteWikiEmbeddingIndexAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldIndexSearchUpdateAndDeleteEmbeddingDocuments() {
        WikiProperties properties = new WikiProperties();
        properties.setStorageRoot(tempDir);
        SqliteWikiEmbeddingIndexAdapter adapter = new SqliteWikiEmbeddingIndexAdapter(properties);

        adapter.applyChanges("space-1", WikiDocumentChangeSet.builder()
                .spaceId("space-1")
                .embeddingUpserts(List.of(embedding("docs/alpha", "Alpha", List.of(1.0d, 0.0d))))
                .deletedPaths(List.of())
                .fullRebuild(false)
                .build());

        List<WikiEmbeddingSearchHit> alphaHits = adapter.search("space-1", List.of(1.0d, 0.0d), 10);
        assertEquals(1, alphaHits.size());
        assertEquals("docs/alpha", alphaHits.getFirst().getPath());
        assertEquals(1, adapter.count("space-1"));
        adapter.applyChanges("space-1", WikiDocumentChangeSet.builder()
                .spaceId("space-1")
                .embeddingUpserts(List.of(embedding("docs/beta", "Beta", List.of(0.0d, 1.0d))))
                .deletedPaths(List.of())
                .fullRebuild(false)
                .build());

        List<WikiEmbeddingSearchHit> betaHits = adapter.search("space-1", List.of(0.0d, 1.0d), 1);
        assertEquals("docs/beta", betaHits.getFirst().getPath());
        assertEquals(2, adapter.count("space-1"));

        adapter.applyChanges("space-1", WikiDocumentChangeSet.builder()
                .spaceId("space-1")
                .deletedPaths(List.of("docs/alpha"))
                .fullRebuild(false)
                .build());

        assertEquals(1, adapter.count("space-1"));
        assertTrue(adapter.listIndexedPaths("space-1").contains("docs/beta"));
    }

    @Test
    void shouldKeepSpaceEmbeddingsIsolatedWhenPathsOverlap() {
        WikiProperties properties = new WikiProperties();
        properties.setStorageRoot(tempDir);
        SqliteWikiEmbeddingIndexAdapter adapter = new SqliteWikiEmbeddingIndexAdapter(properties);

        adapter.applyChanges("space-a", WikiDocumentChangeSet.builder()
                .spaceId("space-a")
                .embeddingUpserts(List.of(embedding("docs/guide", "Alpha", "revision-a", List.of(1.0d, 0.0d))))
                .deletedPaths(List.of())
                .fullRebuild(false)
                .build());
        adapter.applyChanges("space-b", WikiDocumentChangeSet.builder()
                .spaceId("space-b")
                .embeddingUpserts(List.of(embedding("docs/guide", "Beta", "revision-b", List.of(0.0d, 1.0d))))
                .deletedPaths(List.of())
                .fullRebuild(false)
                .build());

        assertEquals("docs/guide", adapter.search("space-a", List.of(1.0d, 0.0d), 1).getFirst().getPath());
        assertEquals("docs/guide", adapter.search("space-b", List.of(0.0d, 1.0d), 1).getFirst().getPath());
        assertEquals(Map.of("docs/guide", "revision-a"), adapter.listIndexedRevisions("space-a"));
        assertEquals(Map.of("docs/guide", "revision-b"), adapter.listIndexedRevisions("space-b"));

        adapter.applyChanges("space-a", WikiDocumentChangeSet.builder()
                .spaceId("space-a")
                .deletedPaths(List.of("docs/guide"))
                .fullRebuild(false)
                .build());

        assertEquals(0, adapter.count("space-a"));
        assertEquals(1, adapter.count("space-b"));
    }

    private WikiEmbeddingDocument embedding(String path, String title, List<Double> vector) {
        return embedding(path, title, title, vector);
    }

    private WikiEmbeddingDocument embedding(String path, String title, String revision, List<Double> vector) {
        return WikiEmbeddingDocument.builder()
                .document(WikiIndexedDocument.builder()
                        .id(path)
                        .path(path)
                        .parentPath("docs")
                        .title(title)
                        .body("body for " + title)
                        .kind(WikiNodeKind.PAGE)
                        .updatedAt(Instant.parse("2026-04-11T00:00:00Z"))
                        .revision(revision)
                        .build())
                .vector(vector)
                .build();
    }
}
