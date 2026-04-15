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

package me.golemcore.brain.adapter.out.index;

import me.golemcore.brain.adapter.out.index.memory.InMemoryWikiEmbeddingIndexAdapter;
import me.golemcore.brain.adapter.out.index.sqlite.SqliteWikiEmbeddingIndexAdapter;
import me.golemcore.brain.application.port.out.WikiEmbeddingIndexPort;
import me.golemcore.brain.config.WikiProperties;
import me.golemcore.brain.domain.WikiDocumentChangeSet;
import me.golemcore.brain.domain.WikiEmbeddingDocument;
import me.golemcore.brain.domain.WikiEmbeddingSearchHit;
import me.golemcore.brain.domain.WikiIndexedDocument;
import me.golemcore.brain.domain.WikiNodeKind;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiEmbeddingIndexPortContractTest {

    @TempDir
    Path tempDir;

    static Stream<Arguments> adapters() {
        Function<Path, WikiEmbeddingIndexPort> sqliteFactory = tempDir -> {
            WikiProperties properties = new WikiProperties();
            properties.setStorageRoot(tempDir);
            return new SqliteWikiEmbeddingIndexAdapter(properties);
        };
        Function<Path, WikiEmbeddingIndexPort> inMemoryFactory = tempDir -> new InMemoryWikiEmbeddingIndexAdapter();
        return Stream.of(
                Arguments.of("sqlite", sqliteFactory),
                Arguments.of("in-memory", inMemoryFactory));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("adapters")
    void shouldStoreAndSearchMultiChunkDocument(String name, Function<Path, WikiEmbeddingIndexPort> factory) {
        WikiEmbeddingIndexPort adapter = factory.apply(tempDir.resolve(name));

        adapter.applyChanges("space-1", WikiDocumentChangeSet.builder()
                .spaceId("space-1")
                .embeddingUpserts(List.of(
                        chunk("docs/guide", 0, "head chunk", List.of(1.0d, 0.0d)),
                        chunk("docs/guide", 1, "tail chunk", List.of(0.0d, 1.0d))))
                .deletedPaths(List.of())
                .fullRebuild(false)
                .build());

        assertEquals(1, adapter.count("space-1"), "count should collapse chunks per path");
        assertEquals(List.of("docs/guide"), adapter.listIndexedPaths("space-1"));
        assertEquals("rev-docs/guide", adapter.listIndexedRevisions("space-1").get("docs/guide"));

        List<WikiEmbeddingSearchHit> hits = adapter.search("space-1", List.of(0.0d, 1.0d), 10);
        assertEquals(1, hits.size(), "search should dedupe by path");
        assertEquals("docs/guide", hits.getFirst().getPath());
        assertTrue(hits.getFirst().getScore() > 0.5d,
                "best chunk should win (got " + hits.getFirst().getScore() + ")");

        adapter.applyChanges("space-1", WikiDocumentChangeSet.builder()
                .spaceId("space-1")
                .deletedPaths(List.of("docs/guide"))
                .fullRebuild(false)
                .build());

        assertEquals(0, adapter.count("space-1"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("adapters")
    void shouldReplaceAllChunksOnReindex(String name, Function<Path, WikiEmbeddingIndexPort> factory) {
        WikiEmbeddingIndexPort adapter = factory.apply(tempDir.resolve(name + "-reindex"));

        adapter.applyChanges("space-1", WikiDocumentChangeSet.builder()
                .spaceId("space-1")
                .embeddingUpserts(List.of(
                        chunk("docs/guide", 0, "old chunk 0", List.of(1.0d, 0.0d)),
                        chunk("docs/guide", 1, "old chunk 1", List.of(0.0d, 1.0d)),
                        chunk("docs/guide", 2, "old chunk 2", List.of(0.0d, 0.0d))))
                .deletedPaths(List.of())
                .fullRebuild(false)
                .build());

        adapter.applyChanges("space-1", WikiDocumentChangeSet.builder()
                .spaceId("space-1")
                .embeddingUpserts(List.of(chunk("docs/guide", 0, "new only chunk", List.of(1.0d, 0.0d))))
                .deletedPaths(List.of())
                .fullRebuild(false)
                .build());

        assertEquals(1, adapter.count("space-1"));
        // Orphaned chunks 1 and 2 must be gone — otherwise old content would pollute
        // search.
        List<WikiEmbeddingSearchHit> staleHits = adapter.search("space-1", List.of(0.0d, 1.0d), 10);
        assertTrue(staleHits.isEmpty() || !staleHits.getFirst().getExcerpt().contains("old"),
                "orphaned chunks should have been deleted");
    }

    private WikiEmbeddingDocument chunk(String path, int chunkIndex, String chunkText, List<Double> vector) {
        return WikiEmbeddingDocument.builder()
                .document(WikiIndexedDocument.builder()
                        .id(path)
                        .path(path)
                        .parentPath(path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : null)
                        .title(path)
                        .body("body")
                        .kind(WikiNodeKind.PAGE)
                        .updatedAt(Instant.parse("2026-04-11T00:00:00Z"))
                        .revision("rev-" + path)
                        .build())
                .chunkIndex(chunkIndex)
                .chunkText(chunkText)
                .vector(vector)
                .build();
    }
}
