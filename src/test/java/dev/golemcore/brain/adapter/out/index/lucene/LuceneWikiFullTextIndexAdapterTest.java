package dev.golemcore.brain.adapter.out.index.lucene;

import dev.golemcore.brain.config.WikiProperties;
import dev.golemcore.brain.domain.WikiDocumentChangeSet;
import dev.golemcore.brain.domain.WikiIndexedDocument;
import dev.golemcore.brain.domain.WikiNodeKind;
import dev.golemcore.brain.domain.WikiSearchHit;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuceneWikiFullTextIndexAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldIndexSearchUpdateAndDeleteDocuments() {
        WikiProperties properties = new WikiProperties();
        properties.setStorageRoot(tempDir);
        LuceneWikiFullTextIndexAdapter adapter = new LuceneWikiFullTextIndexAdapter(properties);

        adapter.applyChanges("space-1", WikiDocumentChangeSet.builder()
                .spaceId("space-1")
                .upserts(List.of(document("docs/guide", "Alpha guide", "deployment checklist", "revision-1")))
                .deletedPaths(List.of())
                .fullRebuild(false)
                .build());

        List<WikiSearchHit> hits = adapter.search("space-1", "deployment", 10);
        assertEquals(1, hits.size());
        assertEquals("docs/guide", hits.getFirst().getPath());
        assertEquals(Map.of("docs/guide", "revision-1"), adapter.listIndexedRevisions("space-1"));

        adapter.applyChanges("space-1", WikiDocumentChangeSet.builder()
                .spaceId("space-1")
                .upserts(List.of(document("docs/guide", "Alpha guide", "incident response", "revision-2")))
                .deletedPaths(List.of())
                .fullRebuild(false)
                .build());

        assertTrue(adapter.search("space-1", "deployment", 10).isEmpty());
        assertEquals(1, adapter.search("space-1", "incident", 10).size());
        assertEquals(Map.of("docs/guide", "revision-2"), adapter.listIndexedRevisions("space-1"));

        adapter.applyChanges("space-1", WikiDocumentChangeSet.builder()
                .spaceId("space-1")
                .upserts(List.of())
                .deletedPaths(List.of("docs/guide"))
                .fullRebuild(false)
                .build());

        assertEquals(0, adapter.count("space-1"));
        assertTrue(adapter.search("space-1", "incident", 10).isEmpty());
    }

    @Test
    void shouldKeepSpaceIndexesIsolatedWhenPathsOverlap() {
        WikiProperties properties = new WikiProperties();
        properties.setStorageRoot(tempDir);
        LuceneWikiFullTextIndexAdapter adapter = new LuceneWikiFullTextIndexAdapter(properties);

        adapter.applyChanges("space-a", WikiDocumentChangeSet.builder()
                .spaceId("space-a")
                .upserts(List.of(document("docs/guide", "Alpha guide", "alpha-only token", "revision-a")))
                .deletedPaths(List.of())
                .fullRebuild(false)
                .build());
        adapter.applyChanges("space-b", WikiDocumentChangeSet.builder()
                .spaceId("space-b")
                .upserts(List.of(document("docs/guide", "Beta guide", "beta-only token", "revision-b")))
                .deletedPaths(List.of())
                .fullRebuild(false)
                .build());

        assertEquals(1, adapter.search("space-a", "alpha", 10).size());
        assertTrue(adapter.search("space-a", "beta", 10).isEmpty());
        assertEquals(1, adapter.search("space-b", "beta", 10).size());
        assertTrue(adapter.search("space-b", "alpha", 10).isEmpty());
        assertEquals(Map.of("docs/guide", "revision-a"), adapter.listIndexedRevisions("space-a"));
        assertEquals(Map.of("docs/guide", "revision-b"), adapter.listIndexedRevisions("space-b"));
    }

    @Test
    void shouldSearchByWildcardMasksAcrossTitleBodyAndPath() {
        WikiProperties properties = new WikiProperties();
        properties.setStorageRoot(tempDir);
        LuceneWikiFullTextIndexAdapter adapter = new LuceneWikiFullTextIndexAdapter(properties);

        adapter.applyChanges("space-1", WikiDocumentChangeSet.builder()
                .spaceId("space-1")
                .upserts(List.of(
                        document("solar-system-known-bodies", "Known solar bodies", "Mercury Venus Earth",
                                "revision-1"),
                        document("missions/voyager-probes", "Voyager probes", "Interstellar mission notes",
                                "revision-1"),
                        document("operations/runbook", "Operations Runbook", "Deployment checklist", "revision-1")))
                .deletedPaths(List.of())
                .fullRebuild(false)
                .build());

        assertEquals(List.of("solar-system-known-bodies"), adapter.search("space-1", "solar*", 10)
                .stream()
                .map(WikiSearchHit::getPath)
                .toList());
        assertEquals(List.of("missions/voyager-probes"), adapter.search("space-1", "inter*", 10)
                .stream()
                .map(WikiSearchHit::getPath)
                .toList());
        assertEquals(List.of("operations/runbook"), adapter.search("space-1", "runbo?k", 10)
                .stream()
                .map(WikiSearchHit::getPath)
                .toList());
    }

    @Test
    void shouldApplyAllMaskTermsWhenWildcardQueryContainsSeveralTokens() {
        WikiProperties properties = new WikiProperties();
        properties.setStorageRoot(tempDir);
        LuceneWikiFullTextIndexAdapter adapter = new LuceneWikiFullTextIndexAdapter(properties);

        adapter.applyChanges("space-1", WikiDocumentChangeSet.builder()
                .spaceId("space-1")
                .upserts(List.of(
                        document("solar-system-known-bodies", "Known solar bodies", "Mercury Venus Earth",
                                "revision-1"),
                        document("solar-energy-notes", "Solar energy", "Panels and batteries", "revision-1")))
                .deletedPaths(List.of())
                .fullRebuild(false)
                .build());

        assertEquals(List.of("solar-system-known-bodies"), adapter.search("space-1", "solar* bod*", 10)
                .stream()
                .map(WikiSearchHit::getPath)
                .toList());
    }

    private WikiIndexedDocument document(String path, String title, String body, String revision) {
        return WikiIndexedDocument.builder()
                .id(path)
                .path(path)
                .parentPath("docs")
                .title(title)
                .body(body)
                .kind(WikiNodeKind.PAGE)
                .updatedAt(Instant.parse("2026-04-11T00:00:00Z"))
                .revision(revision)
                .build();
    }

}
