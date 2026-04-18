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

package me.golemcore.brain.adapter.out.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import me.golemcore.brain.config.WikiProperties;
import me.golemcore.brain.domain.WikiAccessStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemWikiAccessStatsAdapterTest {

    @TempDir
    Path tempDir;

    private FileSystemWikiAccessStatsAdapter newAdapter() {
        return newAdapter(Clock.systemUTC());
    }

    private FileSystemWikiAccessStatsAdapter newAdapter(Clock clock) {
        WikiProperties properties = new WikiProperties();
        properties.setStorageRoot(tempDir);
        return new FileSystemWikiAccessStatsAdapter(properties, clock);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;
        private final ZoneId zone;

        MutableClock(Instant start) {
            this(start, ZoneOffset.UTC);
        }

        MutableClock(Instant start, ZoneId zone) {
            this.instant = start;
            this.zone = zone;
        }

        void advance(Duration delta) {
            instant = instant.plus(delta);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId overrideZone) {
            return new MutableClock(instant, overrideZone);
        }
    }

    private Path statsFile(String spaceId) {
        return tempDir.resolve("spaces").resolve(spaceId).resolve(".access.json");
    }

    @Test
    void recordAccessCreatesEntryAndIncrementsCounter() {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();

        WikiAccessStats first = adapter.recordAccess("space-1", "guides/intro");
        WikiAccessStats second = adapter.recordAccess("space-1", "guides/intro");

        assertEquals(1L, first.getAccessCount());
        assertEquals(2L, second.getAccessCount());
        assertEquals("guides/intro", second.getPath());
        assertTrue(Files.exists(statsFile("space-1")));
    }

    @Test
    void recordAccessPersistsAndReloadsAfterNewAdapter() {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();
        adapter.recordAccess("space-1", "a");
        adapter.recordAccess("space-1", "a");
        adapter.recordAccess("space-1", "b");

        FileSystemWikiAccessStatsAdapter reloaded = newAdapter();
        Optional<WikiAccessStats> a = reloaded.getStats("space-1", "a");
        Optional<WikiAccessStats> b = reloaded.getStats("space-1", "b");

        assertTrue(a.isPresent());
        assertEquals(2L, a.get().getAccessCount());
        assertTrue(b.isPresent());
        assertEquals(1L, b.get().getAccessCount());
    }

    @Test
    void getStatsReturnsEmptyWhenPathMissing() {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();

        assertFalse(adapter.getStats("space-1", "missing").isPresent());
    }

    @Test
    void tolerantParseRecoversFromMalformedJson() throws IOException {
        Path file = statsFile("space-1");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{not valid json", StandardCharsets.UTF_8);

        FileSystemWikiAccessStatsAdapter adapter = newAdapter();

        assertTrue(adapter.listTop("space-1", 10).isEmpty(),
                "malformed JSON should be treated as empty, not propagated as an exception");
        WikiAccessStats recovered = adapter.recordAccess("space-1", "page");
        assertEquals(1L, recovered.getAccessCount(),
                "after a malformed-json reset, subsequent recordAccess should start counting fresh");
    }

    @Test
    void tolerantParseRecoversFromNonObjectJsonRoot() throws IOException {
        Path file = statsFile("space-1");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "[\"array\", \"is\", \"not\", \"an\", \"object\"]", StandardCharsets.UTF_8);

        FileSystemWikiAccessStatsAdapter adapter = newAdapter();

        assertTrue(adapter.listTop("space-1", 10).isEmpty());
    }

    @Test
    void listTopOrdersByCountThenByLastAccessed() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        FileSystemWikiAccessStatsAdapter adapter = newAdapter(clock);
        adapter.recordAccess("space-1", "a");
        clock.advance(Duration.ofSeconds(1));
        adapter.recordAccess("space-1", "b");
        adapter.recordAccess("space-1", "b");
        clock.advance(Duration.ofSeconds(1));
        adapter.recordAccess("space-1", "c");

        List<WikiAccessStats> top = adapter.listTop("space-1", 10);

        assertEquals(List.of("b", "c", "a"), top.stream().map(WikiAccessStats::getPath).toList());
    }

    @Test
    void listTopWithZeroLimitReturnsAll() {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();
        adapter.recordAccess("space-1", "a");
        adapter.recordAccess("space-1", "b");
        adapter.recordAccess("space-1", "c");

        List<WikiAccessStats> top = adapter.listTop("space-1", 0);

        assertEquals(3, top.size());
    }

    @Test
    void listTopWithNegativeLimitReturnsAll() {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();
        adapter.recordAccess("space-1", "a");
        adapter.recordAccess("space-1", "b");

        List<WikiAccessStats> top = adapter.listTop("space-1", -5);

        assertEquals(2, top.size());
    }

    @Test
    void listTopAppliesPositiveLimit() {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();
        adapter.recordAccess("space-1", "a");
        adapter.recordAccess("space-1", "b");
        adapter.recordAccess("space-1", "c");

        List<WikiAccessStats> top = adapter.listTop("space-1", 2);

        assertEquals(2, top.size());
    }

    @Test
    void listTopReturnsEmptyWhenSpaceHasNoStats() {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();

        assertTrue(adapter.listTop("empty-space", 5).isEmpty());
    }

    @Test
    void renderEscapesQuotesAndBackslashesInPath() throws IOException {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();
        adapter.recordAccess("space-1", "weird\"path\\with/stuff");

        String raw = Files.readString(statsFile("space-1"), StandardCharsets.UTF_8);
        assertTrue(raw.contains("weird\\\"path\\\\with/stuff"), () -> "Unescaped output: " + raw);
    }

    @Test
    void parseReturnsEmptyForEmptyFile() throws IOException {
        Path file = statsFile("space-1");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "", StandardCharsets.UTF_8);

        FileSystemWikiAccessStatsAdapter adapter = newAdapter();

        assertTrue(adapter.listTop("space-1", 10).isEmpty());
    }

    @Test
    void parseReturnsEmptyForBlankFile() throws IOException {
        Path file = statsFile("space-1");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "   \n  \t ", StandardCharsets.UTF_8);

        FileSystemWikiAccessStatsAdapter adapter = newAdapter();

        assertTrue(adapter.listTop("space-1", 10).isEmpty());
    }

    @Test
    void parseReturnsEmptyForEmptyObject() throws IOException {
        Path file = statsFile("space-1");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{}", StandardCharsets.UTF_8);

        FileSystemWikiAccessStatsAdapter adapter = newAdapter();

        assertTrue(adapter.listTop("space-1", 10).isEmpty());
    }

    @Test
    void parseReturnsEmptyForObjectWithOnlyWhitespace() throws IOException {
        Path file = statsFile("space-1");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{   \n  }", StandardCharsets.UTF_8);

        FileSystemWikiAccessStatsAdapter adapter = newAdapter();

        assertTrue(adapter.listTop("space-1", 10).isEmpty());
    }

    @Test
    void parseReturnsEmptyForNonObjectJson() throws IOException {
        Path file = statsFile("space-1");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "[]", StandardCharsets.UTF_8);

        FileSystemWikiAccessStatsAdapter adapter = newAdapter();

        assertTrue(adapter.listTop("space-1", 10).isEmpty());
    }

    @Test
    void parseReturnsEmptyForObjectMissingClosingBrace() throws IOException {
        Path file = statsFile("space-1");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"a\":{\"accessCount\":1,\"lastAccessedAt\":\"2026-01-01T00:00:00Z\"}",
                StandardCharsets.UTF_8);

        FileSystemWikiAccessStatsAdapter adapter = newAdapter();

        assertTrue(adapter.listTop("space-1", 10).isEmpty());
    }

    @Test
    void parseStopsAtUnterminatedKeyQuote() throws IOException {
        Path file = statsFile("space-1");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"abc}", StandardCharsets.UTF_8);

        FileSystemWikiAccessStatsAdapter adapter = newAdapter();

        assertTrue(adapter.listTop("space-1", 10).isEmpty());
    }

    @Test
    void parseStopsWhenKeyIsNotString() throws IOException {
        Path file = statsFile("space-1");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{123:{}}", StandardCharsets.UTF_8);

        FileSystemWikiAccessStatsAdapter adapter = newAdapter();

        assertTrue(adapter.listTop("space-1", 10).isEmpty());
    }

    @Test
    void parseStopsWhenMissingEntryObject() throws IOException {
        Path file = statsFile("space-1");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"a\":null}", StandardCharsets.UTF_8);

        FileSystemWikiAccessStatsAdapter adapter = newAdapter();

        assertTrue(adapter.listTop("space-1", 10).isEmpty());
    }

    @Test
    void parseReturnsEmptyForMalformedJson() throws IOException {
        Path file = statsFile("space-1");
        Files.createDirectories(file.getParent());
        Files.writeString(
                file,
                "{\"a\":{\"accessCount\":notANumber,\"lastAccessedAt\":\"bogus\",\"extra\":\"x\",lonely}}",
                StandardCharsets.UTF_8);

        FileSystemWikiAccessStatsAdapter adapter = newAdapter();

        assertTrue(adapter.listTop("space-1", 10).isEmpty());
    }

    @Test
    void parseAppliesDefaultsForMissingEntryFields() throws IOException {
        Path file = statsFile("space-1");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"a\":{},\"b\":{\"extra\":\"x\"}}", StandardCharsets.UTF_8);

        FileSystemWikiAccessStatsAdapter adapter = newAdapter();
        List<WikiAccessStats> top = adapter.listTop("space-1", 10);

        assertEquals(2, top.size());
        for (WikiAccessStats stats : top) {
            assertEquals(0L, stats.getAccessCount());
            assertEquals(Instant.EPOCH, stats.getLastAccessedAt());
        }
    }

    @Test
    void parseIgnoresUnparseableLastAccessedTimestamp() throws IOException {
        Path file = statsFile("space-1");
        Files.createDirectories(file.getParent());
        Files.writeString(
                file,
                "{\"a\":{\"accessCount\":3,\"lastAccessedAt\":\"not-a-timestamp\"}}",
                StandardCharsets.UTF_8);

        FileSystemWikiAccessStatsAdapter adapter = newAdapter();
        List<WikiAccessStats> top = adapter.listTop("space-1", 10);

        assertEquals(1, top.size());
        assertEquals(3L, top.get(0).getAccessCount());
        assertEquals(Instant.EPOCH, top.get(0).getLastAccessedAt());
    }

    @Test
    void parseReadsMultipleValidEntriesSeparatedByCommaAndWhitespace() throws IOException {
        Path file = statsFile("space-1");
        Files.createDirectories(file.getParent());
        Files.writeString(
                file,
                "{\n  \"a\":{\"accessCount\":3,\"lastAccessedAt\":\"2026-01-01T00:00:00Z\"} , \n"
                        + "\"b\":{\"accessCount\":1,\"lastAccessedAt\":\"2026-01-02T00:00:00Z\"}\n}",
                StandardCharsets.UTF_8);

        FileSystemWikiAccessStatsAdapter adapter = newAdapter();
        List<WikiAccessStats> top = adapter.listTop("space-1", 10);

        assertEquals(2, top.size());
        assertEquals("a", top.get(0).getPath());
        assertEquals(3L, top.get(0).getAccessCount());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), top.get(0).getLastAccessedAt());
        assertEquals("b", top.get(1).getPath());
    }

    @Test
    void concurrentRecordAndListDoesNotThrow() throws InterruptedException {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();
        int writers = 4;
        int readers = 4;
        int iterations = 200;
        ExecutorService executor = Executors.newFixedThreadPool(writers + readers);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(writers + readers);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            for (int w = 0; w < writers; w++) {
                final int id = w;
                executor.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < iterations; i++) {
                            adapter.recordAccess("space-1", "path-" + (i % 20) + "-w" + id);
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                });
            }
            for (int r = 0; r < readers; r++) {
                executor.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < iterations; i++) {
                            adapter.listTop("space-1", 5);
                            adapter.getStats("space-1", "path-" + (i % 20) + "-w0");
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "concurrent workload timed out");
        } finally {
            executor.shutdownNow();
        }
        if (failure.get() != null) {
            throw new AssertionError("Concurrent access threw exception", failure.get());
        }
        long totalExpected = (long) writers * iterations;
        long totalObserved = adapter.listTop("space-1", 0).stream().mapToLong(WikiAccessStats::getAccessCount).sum();
        assertEquals(totalExpected, totalObserved, "Access counter lost updates under concurrency");
    }

    @Test
    void failedPersistDoesNotMutateInMemoryCounter() throws IOException {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();
        adapter.recordAccess("space-1", "a");
        long before = adapter.getStats("space-1", "a").orElseThrow().getAccessCount();

        Path tmp = statsFile("space-1").resolveSibling(".access.json.tmp");
        Files.createDirectories(tmp);
        try {
            assertThrows(IllegalStateException.class, () -> adapter.recordAccess("space-1", "a"));

            long after = adapter.getStats("space-1", "a").orElseThrow().getAccessCount();
            assertEquals(before, after, "In-memory counter must not advance when persist fails");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    void failedPersistDeletesLeftoverTmpFile() throws IOException {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();
        adapter.recordAccess("space-1", "a");

        Path statsPath = statsFile("space-1");
        Path stalePath = statsPath.resolveSibling(".access.json.stale-block");
        Files.createDirectories(stalePath);

        Path tmp = statsPath.resolveSibling(".access.json.tmp");
        try {
            // Write is fine; make ATOMIC_MOVE fail by turning the destination into a
            // directory
            Files.delete(statsPath);
            Files.createDirectories(statsPath);

            assertThrows(IllegalStateException.class, () -> adapter.recordAccess("space-1", "a"));

            assertFalse(Files.exists(tmp), "tmp artifact must be cleaned up on persist failure");
        } finally {
            try {
                Files.walk(statsPath).sorted((a, b) -> b.compareTo(a))
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {
                            }
                        });
            } catch (IOException ignored) {
            }
            Files.deleteIfExists(tmp);
            Files.deleteIfExists(stalePath);
        }
    }

    @Test
    void rejectsSpaceIdWithPathTraversal() {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();

        assertThrows(IllegalArgumentException.class, () -> adapter.recordAccess("../escape", "a"));
        assertThrows(IllegalArgumentException.class, () -> adapter.recordAccess("ok/../nope", "a"));
        assertThrows(IllegalArgumentException.class, () -> adapter.recordAccess("", "a"));
        assertThrows(IllegalArgumentException.class, () -> adapter.getStats("..", "a"));
        assertThrows(IllegalArgumentException.class, () -> adapter.listTop("a/b", 5));
    }

    @Test
    void acceptsValidSpaceIdCharacters() {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();

        adapter.recordAccess("space-1", "p");
        adapter.recordAccess("Space_2", "p");
        adapter.recordAccess("UUID-0123abcd", "p");

        assertEquals(1L, adapter.getStats("space-1", "p").orElseThrow().getAccessCount());
        assertEquals(1L, adapter.getStats("Space_2", "p").orElseThrow().getAccessCount());
    }

    @Test
    void removePathClearsSingleEntry() {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();
        adapter.recordAccess("space-1", "a");
        adapter.recordAccess("space-1", "b");

        adapter.removePath("space-1", "a");

        assertTrue(adapter.getStats("space-1", "a").isEmpty());
        assertEquals(1L, adapter.getStats("space-1", "b").orElseThrow().getAccessCount());
    }

    @Test
    void removePathPersistsRemovalAcrossRestart() {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();
        adapter.recordAccess("space-1", "a");
        adapter.recordAccess("space-1", "b");

        adapter.removePath("space-1", "a");

        FileSystemWikiAccessStatsAdapter reloaded = newAdapter();
        assertTrue(reloaded.getStats("space-1", "a").isEmpty());
        assertTrue(reloaded.getStats("space-1", "b").isPresent());
    }

    @Test
    void removeSubtreeClearsPathAndDescendants() {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();
        adapter.recordAccess("space-1", "sec");
        adapter.recordAccess("space-1", "sec/a");
        adapter.recordAccess("space-1", "sec/b/c");
        adapter.recordAccess("space-1", "other");

        adapter.removeSubtree("space-1", "sec");

        assertTrue(adapter.getStats("space-1", "sec").isEmpty());
        assertTrue(adapter.getStats("space-1", "sec/a").isEmpty());
        assertTrue(adapter.getStats("space-1", "sec/b/c").isEmpty());
        assertTrue(adapter.getStats("space-1", "other").isPresent());
    }

    @Test
    void removePathIsNoopForUnknownSpace() {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();

        adapter.removePath("never-written", "x");
        adapter.removeSubtree("never-written", "x");
    }

    @Test
    void removePathsRemovesMatchingEntriesInBatch() {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();
        adapter.recordAccess("space-1", "a");
        adapter.recordAccess("space-1", "b");
        adapter.recordAccess("space-1", "c");

        adapter.removePaths("space-1", List.of("a", "c"));

        assertTrue(adapter.getStats("space-1", "a").isEmpty());
        assertTrue(adapter.getStats("space-1", "b").isPresent());
        assertTrue(adapter.getStats("space-1", "c").isEmpty());
    }

    @Test
    void removePathsPersistsAcrossRestart() {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();
        adapter.recordAccess("space-1", "a");
        adapter.recordAccess("space-1", "b");

        adapter.removePaths("space-1", List.of("a"));

        FileSystemWikiAccessStatsAdapter reloaded = newAdapter();
        assertTrue(reloaded.getStats("space-1", "a").isEmpty());
        assertTrue(reloaded.getStats("space-1", "b").isPresent());
    }

    @Test
    void removePathsIgnoresNullEntries() {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();
        adapter.recordAccess("space-1", "a");
        adapter.recordAccess("space-1", "b");

        adapter.removePaths("space-1", java.util.Arrays.asList(null, "a", null));

        assertTrue(adapter.getStats("space-1", "a").isEmpty());
        assertTrue(adapter.getStats("space-1", "b").isPresent());
    }

    @Test
    void removePathsIsNoopForEmptyBatch() throws IOException {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();
        adapter.recordAccess("space-1", "a");

        // Corrupt persist destination: if removePaths were to persist, this would
        // throw.
        Files.delete(statsFile("space-1"));
        Files.createDirectories(statsFile("space-1"));

        adapter.removePaths("space-1", List.of());
        adapter.removePaths("space-1", null);
    }

    @Test
    void removePathsIsNoopWhenNoPathsMatch() throws IOException {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();
        adapter.recordAccess("space-1", "a");

        // Corrupt persist destination: if removePaths were to persist, this would
        // throw.
        Files.delete(statsFile("space-1"));
        Files.createDirectories(statsFile("space-1"));

        adapter.removePaths("space-1", List.of("x", "y"));
    }

    @Test
    void removePathsIsNoopForUnknownSpace() {
        FileSystemWikiAccessStatsAdapter adapter = newAdapter();

        adapter.removePaths("never-written", List.of("x", "y"));
    }

    @Test
    void removePathsEquivalentToRemovePathForSingleEntry() {
        FileSystemWikiAccessStatsAdapter a = newAdapter();
        a.recordAccess("space-1", "x");
        a.recordAccess("space-1", "y");
        a.removePath("space-1", "x");

        FileSystemWikiAccessStatsAdapter b = newAdapter();
        b.recordAccess("space-2", "x");
        b.recordAccess("space-2", "y");
        b.removePaths("space-2", List.of("x"));

        assertTrue(a.getStats("space-1", "x").isEmpty());
        assertTrue(a.getStats("space-1", "y").isPresent());
        assertTrue(b.getStats("space-2", "x").isEmpty());
        assertTrue(b.getStats("space-2", "y").isPresent());
    }

    @Test
    void readFromDiskWrapsIoException() throws IOException {
        Path spaceDir = tempDir.resolve("spaces").resolve("space-broken");
        Files.createDirectories(spaceDir);
        Path statsAsDir = spaceDir.resolve(".access.json");
        Files.createDirectories(statsAsDir);

        FileSystemWikiAccessStatsAdapter adapter = newAdapter();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> adapter.recordAccess("space-broken", "a"));
        assertTrue(ex.getMessage().contains("Failed to read access stats"));
    }
}
