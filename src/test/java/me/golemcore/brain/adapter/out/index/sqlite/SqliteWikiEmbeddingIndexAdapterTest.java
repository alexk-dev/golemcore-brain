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

package me.golemcore.brain.adapter.out.index.sqlite;

import me.golemcore.brain.config.WikiProperties;
import me.golemcore.brain.domain.WikiDocumentChangeSet;
import me.golemcore.brain.domain.WikiEmbeddingDocument;
import me.golemcore.brain.domain.WikiEmbeddingSearchHit;
import me.golemcore.brain.domain.WikiIndexedDocument;
import me.golemcore.brain.domain.WikiNodeKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
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

    @Test
    void shouldRejectQueryEmbeddingWithDifferentDimensionFromStoredVectors() {
        // A dimension mismatch usually means the embedding model changed and
        // the reconciler hasn't rebuilt yet. Failing loudly surfaces the bug
        // instead of returning cosine-similarity garbage against vectors of
        // different shape.
        WikiProperties properties = new WikiProperties();
        properties.setStorageRoot(tempDir);
        SqliteWikiEmbeddingIndexAdapter adapter = new SqliteWikiEmbeddingIndexAdapter(properties);

        adapter.applyChanges("space-1", WikiDocumentChangeSet.builder()
                .spaceId("space-1")
                .embeddingUpserts(List.of(embedding("docs/guide", "Guide", List.of(1.0d, 0.0d))))
                .deletedPaths(List.of())
                .fullRebuild(false)
                .build());

        try {
            adapter.search("space-1", List.of(1.0d, 0.0d, 0.0d), 10);
            assertTrue(false, "expected IllegalStateException for dimension mismatch");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().toLowerCase().contains("dimension"),
                    "message should mention dimension: " + expected.getMessage());
        }
    }

    @Test
    void shouldBuildSearchExcerptFromChunkTextNotRawBody() {
        // Semantic hits should describe the chunk that actually matched, not
        // the entire document body — otherwise the snippet returned to the bot
        // loses its connection to the embedding that scored highest.
        WikiProperties properties = new WikiProperties();
        properties.setStorageRoot(tempDir);
        SqliteWikiEmbeddingIndexAdapter adapter = new SqliteWikiEmbeddingIndexAdapter(properties);

        adapter.applyChanges("space-1", WikiDocumentChangeSet.builder()
                .spaceId("space-1")
                .embeddingUpserts(List.of(
                        chunk("docs/guide", 0, "chunk-marker content that must appear in the excerpt",
                                List.of(1.0d, 0.0d))))
                .deletedPaths(List.of())
                .fullRebuild(false)
                .build());

        WikiEmbeddingSearchHit hit = adapter.search("space-1", List.of(1.0d, 0.0d), 1).getFirst();
        assertTrue(hit.getExcerpt().contains("chunk-marker"),
                "excerpt should come from chunk_text, got: " + hit.getExcerpt());
    }

    @Test
    void shouldMigrateLegacySchemaByRebuildingEmbeddingTable() throws Exception {
        WikiProperties properties = new WikiProperties();
        properties.setStorageRoot(tempDir);
        Path dbPath = tempDir.resolve(".indexes/embeddings/embeddings.sqlite");
        Files.createDirectories(dbPath.getParent());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
                Statement statement = connection.createStatement()) {
            statement.execute("create table wiki_embeddings ("
                    + "space_id text not null, "
                    + "path text not null, "
                    + "id text not null, "
                    + "parent_path text, "
                    + "title text not null, "
                    + "body text not null, "
                    + "kind text not null, "
                    + "revision text, "
                    + "updated_at integer not null, "
                    + "vector blob not null, "
                    + "primary key (space_id, path))");
            statement.execute("insert into wiki_embeddings "
                    + "(space_id, path, id, parent_path, title, body, kind, revision, updated_at, vector) "
                    + "values ('space-1','docs/legacy','docs/legacy','docs','Legacy','body','PAGE',"
                    + "'rev-legacy',0,X'00')");
        }

        SqliteWikiEmbeddingIndexAdapter adapter = new SqliteWikiEmbeddingIndexAdapter(properties);

        // Upsert with two chunks for the same path must succeed on a pre-chunking DB.
        adapter.applyChanges("space-1", WikiDocumentChangeSet.builder()
                .spaceId("space-1")
                .embeddingUpserts(List.of(
                        chunk("docs/reindexed", 0, "head", List.of(1.0d, 0.0d)),
                        chunk("docs/reindexed", 1, "tail", List.of(0.0d, 1.0d))))
                .deletedPaths(List.of())
                .fullRebuild(false)
                .build());

        assertEquals(1, adapter.count("space-1"));
        assertEquals(List.of("docs/reindexed"), adapter.listIndexedPaths("space-1"));
        List<WikiEmbeddingSearchHit> hits = adapter.search("space-1", List.of(0.0d, 1.0d), 10);
        assertEquals("docs/reindexed", hits.getFirst().getPath());
    }

    private WikiEmbeddingDocument chunk(String path, int chunkIndex, String chunkText, List<Double> vector) {
        return WikiEmbeddingDocument.builder()
                .document(WikiIndexedDocument.builder()
                        .id(path)
                        .path(path)
                        .parentPath("docs")
                        .title(path)
                        .body("body for " + path)
                        .kind(WikiNodeKind.PAGE)
                        .updatedAt(Instant.parse("2026-04-11T00:00:00Z"))
                        .revision("rev-" + path)
                        .build())
                .chunkIndex(chunkIndex)
                .chunkText(chunkText)
                .vector(vector)
                .build();
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
