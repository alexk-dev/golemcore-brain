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

package me.golemcore.brain.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import me.golemcore.brain.adapter.out.filesystem.FileSystemWikiRepository;
import me.golemcore.brain.adapter.out.filesystem.space.FileSpaceRepository;
import me.golemcore.brain.adapter.out.index.lucene.LuceneWikiFullTextIndexAdapter;
import me.golemcore.brain.adapter.out.index.sqlite.SqliteWikiEmbeddingIndexAdapter;
import me.golemcore.brain.application.exception.WikiEditConflictException;
import me.golemcore.brain.application.exception.WikiNotFoundException;
import me.golemcore.brain.application.port.out.LlmEmbeddingPort;
import me.golemcore.brain.application.port.out.LlmSettingsRepository;
import me.golemcore.brain.application.service.index.WikiIndexingService;
import me.golemcore.brain.application.service.support.InMemoryWikiAccessStatsPort;
import me.golemcore.brain.application.space.SpaceContextHolder;
import me.golemcore.brain.config.WikiProperties;
import me.golemcore.brain.domain.WikiAccessStats;
import me.golemcore.brain.domain.WikiNodeKind;
import me.golemcore.brain.domain.WikiImportApplyResponse;
import me.golemcore.brain.domain.WikiPage;
import me.golemcore.brain.domain.WikiPageHistoryEntry;
import me.golemcore.brain.domain.WikiPatchOperation;
import me.golemcore.brain.domain.WikiTxOperationType;
import me.golemcore.brain.domain.WikiTxResult;
import me.golemcore.brain.domain.llm.LlmEmbeddingRequest;
import me.golemcore.brain.domain.llm.LlmEmbeddingResponse;
import me.golemcore.brain.domain.llm.LlmSettings;
import me.golemcore.brain.domain.space.Space;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WikiApplicationServiceBranchesTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        SpaceContextHolder.clear();
    }

    @Test
    void patchReplaceSectionAcceptsEmptyBodyAndContentAlreadyEndingWithNewline() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Runbook")
                .slug("runbook")
                .content("# Runbook\n\n## Status\nOld\n\n## Next\nX\n")
                .kind(WikiNodeKind.PAGE)
                .build());
        String revision = service.getPage("runbook").getRevision();

        WikiPage patched = service.patchPage(WikiApplicationService.PatchPageCommand.builder()
                .path("runbook")
                .operation(WikiPatchOperation.REPLACE_SECTION)
                .heading("Status")
                .content("new body\n")
                .expectedRevision(revision)
                .build());

        assertTrue(patched.getContent().contains("new body"));
        assertTrue(patched.getContent().contains("## Next"));

        String revision2 = service.getPage("runbook").getRevision();
        WikiPage empty = service.patchPage(WikiApplicationService.PatchPageCommand.builder()
                .path("runbook")
                .operation(WikiPatchOperation.REPLACE_SECTION)
                .heading("Status")
                .content("")
                .expectedRevision(revision2)
                .build());
        assertTrue(empty.getContent().contains("## Status"));
        assertFalse(empty.getContent().contains("new body"));
    }

    @Test
    void patchReplaceSectionKeepsDeeperHeadingsInsideTargetSection() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Deep")
                .slug("deep")
                .content("## Top\nbody\n### Nested\nsubbody\n## Sibling\nafter\n")
                .kind(WikiNodeKind.PAGE)
                .build());
        String revision = service.getPage("deep").getRevision();

        WikiPage patched = service.patchPage(WikiApplicationService.PatchPageCommand.builder()
                .path("deep")
                .operation(WikiPatchOperation.REPLACE_SECTION)
                .heading("Top")
                .content("fresh\n")
                .expectedRevision(revision)
                .build());

        assertTrue(patched.getContent().contains("## Top"));
        assertTrue(patched.getContent().contains("fresh"));
        assertTrue(patched.getContent().contains("## Sibling"));
        assertFalse(patched.getContent().contains("### Nested"));
    }

    @Test
    void patchReplaceSectionRejectsBlankHeading() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Blank")
                .slug("blank")
                .content("## H\nbody\n")
                .kind(WikiNodeKind.PAGE)
                .build());
        String revision = service.getPage("blank").getRevision();

        assertThrows(IllegalArgumentException.class,
                () -> service.patchPage(WikiApplicationService.PatchPageCommand.builder()
                        .path("blank")
                        .operation(WikiPatchOperation.REPLACE_SECTION)
                        .heading("   ")
                        .content("x")
                        .expectedRevision(revision)
                        .build()));
    }

    @Test
    void patchRejectsUnknownPage() {
        WikiApplicationService service = createService();
        assertThrows(WikiNotFoundException.class,
                () -> service.patchPage(WikiApplicationService.PatchPageCommand.builder()
                        .path("missing")
                        .operation(WikiPatchOperation.APPEND)
                        .content("x")
                        .build()));
    }

    @Test
    void patchAppendAndPrependAdjustBody() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("P")
                .slug("p")
                .content("middle")
                .kind(WikiNodeKind.PAGE)
                .build());
        String rev1 = service.getPage("p").getRevision();

        WikiPage appended = service.patchPage(WikiApplicationService.PatchPageCommand.builder()
                .path("p")
                .operation(WikiPatchOperation.APPEND)
                .content("+end")
                .expectedRevision(rev1)
                .build());
        assertTrue(appended.getContent().endsWith("middle+end"));

        String rev2 = service.getPage("p").getRevision();
        WikiPage prepended = service.patchPage(WikiApplicationService.PatchPageCommand.builder()
                .path("p")
                .operation(WikiPatchOperation.PREPEND)
                .content("start+")
                .expectedRevision(rev2)
                .build());
        assertTrue(prepended.getContent().startsWith("start+"));
    }

    @Test
    void patchReplaceSectionThrowsWhenHeadingMissing() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Miss")
                .slug("miss")
                .content("body only\n")
                .kind(WikiNodeKind.PAGE)
                .build());
        String revision = service.getPage("miss").getRevision();

        assertThrows(IllegalArgumentException.class,
                () -> service.patchPage(WikiApplicationService.PatchPageCommand.builder()
                        .path("miss")
                        .operation(WikiPatchOperation.REPLACE_SECTION)
                        .heading("Absent")
                        .content("x")
                        .expectedRevision(revision)
                        .build()));
    }

    @Test
    void frontmatterCodecParsesTagsAndSummaryVariants() {
        WikiFrontmatterCodec codec = new WikiFrontmatterCodec();

        WikiFrontmatterCodec.Frontmatter summaryOnly = codec.render(List.of(), "only summary here", "Body") == null
                ? null
                : codec.parse(codec.render(List.of(), "only summary here", "Body"));
        assertEquals("only summary here", summaryOnly.summary());
        assertTrue(summaryOnly.tags().isEmpty());
        assertEquals("Body", summaryOnly.remainingBody());

        WikiFrontmatterCodec.Frontmatter tagsOnly = codec.parse(codec.render(List.of("a", "b"), null, "Text"));
        assertEquals(List.of("a", "b"), tagsOnly.tags());
        assertNull(tagsOnly.summary());
        assertEquals("Text", tagsOnly.remainingBody());
    }

    @Test
    void patchApplierReplacesSectionWithoutTouchingOtherSections() {
        WikiPatchApplier applier = new WikiPatchApplier();
        WikiApplicationService.PatchPageCommand command = WikiApplicationService.PatchPageCommand.builder()
                .operation(WikiPatchOperation.REPLACE_SECTION)
                .heading("Status")
                .content("Updated status line.\n")
                .build();

        String patched = applier.apply(
                "# Runbook\n\n## Status\nOld status text.\n\n## Next Steps\nReview backlog.\n",
                command);

        assertTrue(patched.contains("Updated status line."));
        assertTrue(patched.contains("## Next Steps"));
        assertTrue(patched.contains("Review backlog."));
        assertFalse(patched.contains("Old status text."));
        assertEquals("Rewrote section 'Status' (+8 chars).",
                applier.buildSummary(command, "old", "new content"));
        assertEquals("Patch (replace section: Status)",
                applier.buildReason(WikiPatchOperation.REPLACE_SECTION, "Status"));
    }

    @Test
    void frontmatterParsesTagsAndSummaryVariants() {
        WikiApplicationService service = createService();

        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Summary Only")
                .slug("summary-only")
                .content("body")
                .summary("only summary here")
                .kind(WikiNodeKind.PAGE)
                .build());
        WikiPage summaryOnly = service.getPage("summary-only");
        assertEquals("only summary here", summaryOnly.getSummary());
        assertTrue(summaryOnly.getTags().isEmpty());

        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Tags Only")
                .slug("tags-only")
                .content("body")
                .tags(List.of("a", "b"))
                .kind(WikiNodeKind.PAGE)
                .build());
        WikiPage tagsOnly = service.getPage("tags-only");
        assertEquals(List.of("a", "b"), tagsOnly.getTags());
        assertNull(tagsOnly.getSummary());

        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("No Meta")
                .slug("no-meta")
                .content("")
                .kind(WikiNodeKind.PAGE)
                .build());
        WikiPage noMeta = service.getPage("no-meta");
        assertTrue(noMeta.getTags().isEmpty());
        assertNull(noMeta.getSummary());
    }

    @Test
    void frontmatterIgnoresMalformedYamlAndPreservesBody() throws Exception {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Raw")
                .slug("raw")
                .content("placeholder")
                .kind(WikiNodeKind.PAGE)
                .build());

        writeRawMarkdown("raw",
                "# Raw\n\n---\ntags: not-a-list\nsummary: unquoted value\nother: ignored\n---\nActual body\n");

        WikiPage page = service.getPage("raw");
        assertTrue(page.getTags().isEmpty());
        assertEquals("unquoted value", page.getSummary());
        assertTrue(page.getContent().contains("Actual body"));
    }

    @Test
    void frontmatterTrimsBlankTagsAndUnquotesValues() throws Exception {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Clean")
                .slug("clean")
                .content("placeholder")
                .kind(WikiNodeKind.PAGE)
                .build());

        writeRawMarkdown("clean",
                "# Clean\n\n---\ntags: [\"alpha\", \"\", beta ,   ]\nsummary: \"quoted text\"\n---\nBody\n");

        WikiPage page = service.getPage("clean");
        assertEquals(List.of("alpha", "beta"), page.getTags());
        assertEquals("quoted text", page.getSummary());
    }

    @Test
    void frontmatterRequiresLeadingFenceOrIsTreatedAsBody() throws Exception {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Late")
                .slug("late")
                .content("placeholder")
                .kind(WikiNodeKind.PAGE)
                .build());

        writeRawMarkdown("late", "# Late\n\nleading body\n---\ntags: [\"x\"]\n---\nrest\n");

        WikiPage page = service.getPage("late");
        assertTrue(page.getTags().isEmpty());
        assertNull(page.getSummary());
    }

    private void writeRawMarkdown(String slug, String content) throws Exception {
        Path spacesDir = tempDir.resolve("wiki").resolve("spaces");
        Path spaceDir;
        try (var stream = Files.list(spacesDir)) {
            spaceDir = stream.filter(Files::isDirectory).findFirst().orElseThrow();
        }
        Files.writeString(spaceDir.resolve(slug + ".md"), content);
    }

    @Test
    void transactionCreatesUpdatesAndDeletesAtomically() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Exists")
                .slug("exists")
                .content("v1")
                .kind(WikiNodeKind.PAGE)
                .build());
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Doomed")
                .slug("doomed")
                .content("bye")
                .kind(WikiNodeKind.PAGE)
                .build());
        String revision = service.getPage("exists").getRevision();

        WikiTxResult result = service.applyTransaction(WikiApplicationService.TransactionCommand.builder()
                .operations(List.of(
                        WikiApplicationService.TxOpCommand.builder()
                                .op(WikiTxOperationType.CREATE)
                                .parentPath("")
                                .title("Made")
                                .slug("made")
                                .content("fresh")
                                .kind(WikiNodeKind.PAGE)
                                .build(),
                        WikiApplicationService.TxOpCommand.builder()
                                .op(WikiTxOperationType.UPDATE)
                                .path("exists")
                                .title("Exists")
                                .slug("exists")
                                .content("v2")
                                .expectedRevision(revision)
                                .build(),
                        WikiApplicationService.TxOpCommand.builder()
                                .op(WikiTxOperationType.DELETE)
                                .path("doomed")
                                .build()))
                .build());

        assertEquals(3, result.getResults().size());
        assertEquals("made", result.getResults().get(0).getPath());
        assertEquals("exists", result.getResults().get(1).getPath());
        assertEquals("doomed", result.getResults().get(2).getPath());
        assertNotNull(result.getResults().get(1).getRevision());
        assertNull(result.getResults().get(2).getRevision());

        assertEquals("v2", service.getPage("exists").getContent());
        assertThrows(WikiNotFoundException.class, () -> service.getPage("doomed"));
        assertEquals("fresh", service.getPage("made").getContent());
    }

    @Test
    void transactionSkipsRevisionCheckWhenExpectedRevisionMissingOrBlank() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Loose")
                .slug("loose")
                .content("a")
                .kind(WikiNodeKind.PAGE)
                .build());

        service.applyTransaction(WikiApplicationService.TransactionCommand.builder()
                .operations(List.of(
                        WikiApplicationService.TxOpCommand.builder()
                                .op(WikiTxOperationType.UPDATE)
                                .path("loose")
                                .title("Loose")
                                .slug("loose")
                                .content("b")
                                .expectedRevision("")
                                .build()))
                .build());
        assertEquals("b", service.getPage("loose").getContent());

        service.applyTransaction(WikiApplicationService.TransactionCommand.builder()
                .operations(List.of(
                        WikiApplicationService.TxOpCommand.builder()
                                .op(WikiTxOperationType.UPDATE)
                                .path("loose")
                                .title("Loose")
                                .slug("loose")
                                .content("c")
                                .build()))
                .build());
        assertEquals("c", service.getPage("loose").getContent());
    }

    @Test
    void transactionConflictRejectsBatchBeforePersistingAnything() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Tgt")
                .slug("tgt")
                .content("orig")
                .kind(WikiNodeKind.PAGE)
                .build());

        assertThrows(WikiEditConflictException.class,
                () -> service.applyTransaction(WikiApplicationService.TransactionCommand.builder()
                        .operations(List.of(
                                WikiApplicationService.TxOpCommand.builder()
                                        .op(WikiTxOperationType.CREATE)
                                        .parentPath("")
                                        .title("Ghost")
                                        .slug("ghost")
                                        .content("")
                                        .kind(WikiNodeKind.PAGE)
                                        .build(),
                                WikiApplicationService.TxOpCommand.builder()
                                        .op(WikiTxOperationType.UPDATE)
                                        .path("tgt")
                                        .title("Tgt")
                                        .slug("tgt")
                                        .content("new")
                                        .expectedRevision("not-real")
                                        .build()))
                        .build()));

        assertThrows(WikiNotFoundException.class, () -> service.getPage("ghost"));
        assertEquals("orig", service.getPage("tgt").getContent());
    }

    @Test
    void transactionWithNullOperationsIsNoop() {
        WikiApplicationService service = createService();

        WikiTxResult result = service.applyTransaction(
                WikiApplicationService.TransactionCommand.builder().operations(null).build());

        assertTrue(result.getResults().isEmpty());
    }

    @Test
    void transactionUpdateMissingPathFailsDuringPrevalidation() {
        WikiApplicationService service = createService();

        assertThrows(WikiNotFoundException.class,
                () -> service.applyTransaction(WikiApplicationService.TransactionCommand.builder()
                        .operations(List.of(
                                WikiApplicationService.TxOpCommand.builder()
                                        .op(WikiTxOperationType.UPDATE)
                                        .path("ghost-path")
                                        .title("g")
                                        .slug("g")
                                        .content("")
                                        .expectedRevision("any")
                                        .build()))
                        .build()));
    }

    @Test
    void transactionUpdateWithoutRevisionFailsBeforePersistingPriorCreate() {
        WikiApplicationService service = createService();

        assertThrows(WikiNotFoundException.class,
                () -> service.applyTransaction(WikiApplicationService.TransactionCommand.builder()
                        .operations(List.of(
                                WikiApplicationService.TxOpCommand.builder()
                                        .op(WikiTxOperationType.CREATE)
                                        .parentPath("")
                                        .title("Fresh")
                                        .slug("fresh")
                                        .content("x")
                                        .kind(WikiNodeKind.PAGE)
                                        .build(),
                                WikiApplicationService.TxOpCommand.builder()
                                        .op(WikiTxOperationType.UPDATE)
                                        .path("absent")
                                        .title("absent")
                                        .slug("absent")
                                        .content("")
                                        .build()))
                        .build()));

        assertThrows(WikiNotFoundException.class, () -> service.getPage("fresh"));
    }

    @Test
    void transactionDeleteMissingPathFailsBeforePersistingPriorCreate() {
        WikiApplicationService service = createService();

        assertThrows(WikiNotFoundException.class,
                () -> service.applyTransaction(WikiApplicationService.TransactionCommand.builder()
                        .operations(List.of(
                                WikiApplicationService.TxOpCommand.builder()
                                        .op(WikiTxOperationType.CREATE)
                                        .parentPath("")
                                        .title("First")
                                        .slug("first")
                                        .content("body")
                                        .kind(WikiNodeKind.PAGE)
                                        .build(),
                                WikiApplicationService.TxOpCommand.builder()
                                        .op(WikiTxOperationType.DELETE)
                                        .path("not-there")
                                        .build()))
                        .build()));

        assertThrows(WikiNotFoundException.class, () -> service.getPage("first"));
    }

    @Test
    void transactionCreateWithBlankTitleFailsBeforePersistingPriorCreate() {
        WikiApplicationService service = createService();

        assertThrows(IllegalArgumentException.class,
                () -> service.applyTransaction(WikiApplicationService.TransactionCommand.builder()
                        .operations(List.of(
                                WikiApplicationService.TxOpCommand.builder()
                                        .op(WikiTxOperationType.CREATE)
                                        .parentPath("")
                                        .title("Ok")
                                        .slug("ok")
                                        .content("body")
                                        .kind(WikiNodeKind.PAGE)
                                        .build(),
                                WikiApplicationService.TxOpCommand.builder()
                                        .op(WikiTxOperationType.CREATE)
                                        .parentPath("")
                                        .title("   ")
                                        .slug("blank-title")
                                        .content("")
                                        .kind(WikiNodeKind.PAGE)
                                        .build()))
                        .build()));

        assertThrows(WikiNotFoundException.class, () -> service.getPage("ok"));
    }

    @Test
    void transactionDuplicateCreateTargetsFailsBeforePersistingAnything() {
        WikiApplicationService service = createService();

        assertThrows(IllegalArgumentException.class,
                () -> service.applyTransaction(WikiApplicationService.TransactionCommand.builder()
                        .operations(List.of(
                                WikiApplicationService.TxOpCommand.builder()
                                        .op(WikiTxOperationType.CREATE)
                                        .parentPath("")
                                        .title("Same")
                                        .slug("same")
                                        .content("first")
                                        .kind(WikiNodeKind.PAGE)
                                        .build(),
                                WikiApplicationService.TxOpCommand.builder()
                                        .op(WikiTxOperationType.CREATE)
                                        .parentPath("")
                                        .title("Same Again")
                                        .slug("same")
                                        .content("second")
                                        .kind(WikiNodeKind.PAGE)
                                        .build()))
                        .build()));

        assertThrows(WikiNotFoundException.class, () -> service.getPage("same"));
    }

    @Test
    void ensurePageReturnsExistingPageWithoutCreating() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Already")
                .slug("already")
                .content("body")
                .kind(WikiNodeKind.PAGE)
                .build());

        WikiPage ensured = service.ensurePage("already", "Ignored Title");

        assertEquals("already", ensured.getPath());
        assertEquals("Already", ensured.getTitle());
    }

    @Test
    void ensurePageCreatesTopLevelPageUsingHumanizedSlugWhenTitleMissing() {
        WikiApplicationService service = createService();

        WikiPage ensured = service.ensurePage("top-only", "");

        assertEquals("top-only", ensured.getPath());
        assertEquals("Top Only", ensured.getTitle());
    }

    @Test
    void ensurePageCreatesNestedPageFromPath() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Parent")
                .slug("parent")
                .content("")
                .kind(WikiNodeKind.SECTION)
                .build());

        WikiPage ensured = service.ensurePage("parent/child", null);

        assertEquals("parent/child", ensured.getPath());
        assertEquals("Child", ensured.getTitle());
    }

    @Test
    void patchReplaceSectionAcceptsContentWithoutTrailingNewline() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Tail")
                .slug("tail")
                .content("## H\nold\n## Next\nstay\n")
                .kind(WikiNodeKind.PAGE)
                .build());
        String revision = service.getPage("tail").getRevision();

        WikiPage patched = service.patchPage(WikiApplicationService.PatchPageCommand.builder()
                .path("tail")
                .operation(WikiPatchOperation.REPLACE_SECTION)
                .heading("H")
                .content("no newline")
                .expectedRevision(revision)
                .build());

        assertTrue(patched.getContent().contains("no newline"));
        assertTrue(patched.getContent().contains("## Next"));
    }

    @Test
    void patchReplaceSectionHandlesHeadingAsLastLine() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("LastHead")
                .slug("lasthead")
                .content("intro\n## Only")
                .kind(WikiNodeKind.PAGE)
                .build());
        String revision = service.getPage("lasthead").getRevision();

        WikiPage patched = service.patchPage(WikiApplicationService.PatchPageCommand.builder()
                .path("lasthead")
                .operation(WikiPatchOperation.REPLACE_SECTION)
                .heading("Only")
                .content("fresh content\n")
                .expectedRevision(revision)
                .build());

        assertTrue(patched.getContent().contains("fresh content"));
    }

    @Test
    void patchReplaceSectionMatchesHeadingCaseSensitively() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Case")
                .slug("case")
                .content("## status\nlower\n## Status\nupper\n")
                .kind(WikiNodeKind.PAGE)
                .build());
        String revision = service.getPage("case").getRevision();

        WikiPage patched = service.patchPage(WikiApplicationService.PatchPageCommand.builder()
                .path("case")
                .operation(WikiPatchOperation.REPLACE_SECTION)
                .heading("Status")
                .content("replaced\n")
                .expectedRevision(revision)
                .build());

        assertTrue(patched.getContent().contains("## status\nlower"));
        assertTrue(patched.getContent().contains("replaced"));
        assertFalse(patched.getContent().contains("upper"));
    }

    @Test
    void patchReplaceSectionSkipsHeadingInsideFencedCodeBlock() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Fenced")
                .slug("fenced")
                .content("```\n## Notes\ninside code\n```\n## Notes\nreal section\n")
                .kind(WikiNodeKind.PAGE)
                .build());
        String revision = service.getPage("fenced").getRevision();

        WikiPage patched = service.patchPage(WikiApplicationService.PatchPageCommand.builder()
                .path("fenced")
                .operation(WikiPatchOperation.REPLACE_SECTION)
                .heading("Notes")
                .content("replaced outside fence\n")
                .expectedRevision(revision)
                .build());

        assertTrue(patched.getContent().contains("inside code"));
        assertTrue(patched.getContent().contains("replaced outside fence"));
        assertFalse(patched.getContent().contains("real section"));
    }

    @Test
    void leadingHashCountRejectsBareHashesAndHashWithoutSpace() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Hashes")
                .slug("hashes")
                .content("intro\n## Real\nkeepme\n#nospace line\n###\n")
                .kind(WikiNodeKind.PAGE)
                .build());
        String revision = service.getPage("hashes").getRevision();

        WikiPage patched = service.patchPage(WikiApplicationService.PatchPageCommand.builder()
                .path("hashes")
                .operation(WikiPatchOperation.REPLACE_SECTION)
                .heading("Real")
                .content("replaced\n")
                .expectedRevision(revision)
                .build());

        assertTrue(patched.getContent().contains("replaced"));
        assertFalse(patched.getContent().contains("keepme"));
    }

    @Test
    void frontmatterHandlesSingleCharQuotedValues() throws Exception {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Short")
                .slug("short")
                .content("placeholder")
                .kind(WikiNodeKind.PAGE)
                .build());

        writeRawMarkdown("short", "# Short\n\n---\ntags: [\"\\\"\", ok]\nsummary: \"\n---\nBody\n");

        WikiPage page = service.getPage("short");
        assertTrue(page.getTags().contains("\""), "tags should include escaped quote: " + page.getTags());
        assertTrue(page.getTags().contains("ok"));
        assertEquals("\"", page.getSummary());
    }

    @Test
    void frontmatterToleratesUnclosedTagsBracketAndMissingSummaryQuotes() throws Exception {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Edge")
                .slug("edge")
                .content("placeholder")
                .kind(WikiNodeKind.PAGE)
                .build());

        writeRawMarkdown("edge",
                "# Edge\n\n---\ntags: [unclosed\nsummary: \"dangling\n---\nBody\n");

        WikiPage page = service.getPage("edge");
        assertTrue(page.getTags().isEmpty());
        assertNotNull(page.getSummary());
    }

    @Test
    void frontmatterOnEmptyBodyReturnsEmptyDefaults() throws Exception {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("EmptyDoc")
                .slug("empty-doc")
                .content("")
                .kind(WikiNodeKind.PAGE)
                .build());

        Path spacesDir = tempDir.resolve("wiki").resolve("spaces");
        Path spaceDir;
        try (var stream = Files.list(spacesDir)) {
            spaceDir = stream.filter(Files::isDirectory).findFirst().orElseThrow();
        }
        Files.writeString(spaceDir.resolve("empty-doc.md"), "");

        WikiPage page = service.getPage("empty-doc");
        assertTrue(page.getTags().isEmpty());
        assertNull(page.getSummary());
        assertEquals("", page.getContent());
    }

    @Test
    void renderFrontmatterSkipsEmptyCollectionsAndBlankSummary() {
        WikiApplicationService service = createService();

        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("NoMetaExplicit")
                .slug("no-meta-explicit")
                .content("body")
                .tags(List.of())
                .summary("   ")
                .kind(WikiNodeKind.PAGE)
                .build());

        WikiPage page = service.getPage("no-meta-explicit");
        assertTrue(page.getTags().isEmpty());
        assertNull(page.getSummary());
        assertFalse(page.getContent().contains("---"));
    }

    @Test
    void createPageRejectsTooManyTags() {
        WikiApplicationService service = createService();
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < 33; i++) {
            tooMany.add("tag-" + i);
        }

        assertThrows(IllegalArgumentException.class,
                () -> service.createPage(WikiApplicationService.CreatePageCommand.builder()
                        .parentPath("")
                        .title("Too Many")
                        .slug("too-many")
                        .tags(tooMany)
                        .content("body")
                        .kind(WikiNodeKind.PAGE)
                        .build()));
    }

    @Test
    void createPageRejectsOversizedTag() {
        WikiApplicationService service = createService();
        String huge = "a".repeat(65);

        assertThrows(IllegalArgumentException.class,
                () -> service.createPage(WikiApplicationService.CreatePageCommand.builder()
                        .parentPath("")
                        .title("Huge Tag")
                        .slug("huge-tag")
                        .tags(List.of(huge))
                        .content("body")
                        .kind(WikiNodeKind.PAGE)
                        .build()));
    }

    @Test
    void createPageRejectsOversizedSummary() {
        WikiApplicationService service = createService();
        String huge = "a".repeat(1001);

        assertThrows(IllegalArgumentException.class,
                () -> service.createPage(WikiApplicationService.CreatePageCommand.builder()
                        .parentPath("")
                        .title("Huge Summary")
                        .slug("huge-summary")
                        .summary(huge)
                        .content("body")
                        .kind(WikiNodeKind.PAGE)
                        .build()));
    }

    @Test
    void markdownImportPreservesBodyLineAdjacentToH1() throws Exception {
        WikiApplicationService service = createService();
        byte[] archive = singleEntryZip("doc.md", "# Title\nFirst body line\nSecond body line\n");

        WikiImportApplyResponse response = service.applyMarkdownImport(new ByteArrayInputStream(archive));

        assertEquals(1, response.getImportedCount());
        WikiPage page = service.getPage("doc");
        assertTrue(page.getContent().contains("First body line"),
                "expected 'First body line' in content: " + page.getContent());
        assertTrue(page.getContent().contains("Second body line"));
    }

    @Test
    void patchHistorySummaryDiffersFromReason() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Hist")
                .slug("hist")
                .content("intro\n")
                .kind(WikiNodeKind.PAGE)
                .build());
        String revision = service.getPage("hist").getRevision();

        service.patchPage(WikiApplicationService.PatchPageCommand.builder()
                .path("hist")
                .operation(WikiPatchOperation.APPEND)
                .content("appended tail line\n")
                .expectedRevision(revision)
                .actor("tester")
                .build());

        List<WikiPageHistoryEntry> history = service.getPageHistory("hist");
        assertFalse(history.isEmpty());
        WikiPageHistoryEntry latest = history.get(0);
        assertNotNull(latest.getReason());
        assertNotNull(latest.getSummary());
        assertNotEquals(latest.getReason(), latest.getSummary(),
                "reason and summary should describe different granularities");
    }

    @Test
    void noOpPatchSummaryHasNoZeroDeltaNoise() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Noop")
                .slug("noop")
                .content("body\n")
                .kind(WikiNodeKind.PAGE)
                .build());
        String revision = service.getPage("noop").getRevision();

        service.patchPage(WikiApplicationService.PatchPageCommand.builder()
                .path("noop")
                .operation(WikiPatchOperation.APPEND)
                .content("")
                .expectedRevision(revision)
                .actor("tester")
                .build());

        WikiPageHistoryEntry latest = service.getPageHistory("noop").get(0);
        assertFalse(latest.getSummary().contains("+0"),
                "no-op patch summary should not contain '+0' chars noise: " + latest.getSummary());
        assertFalse(latest.getSummary().contains("-0"),
                "no-op patch summary should not contain '-0' chars noise: " + latest.getSummary());
    }

    @Test
    void noOpPrependSummaryHasNoZeroDeltaNoise() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Prep")
                .slug("prep")
                .content("body\n")
                .kind(WikiNodeKind.PAGE)
                .build());
        String revision = service.getPage("prep").getRevision();

        service.patchPage(WikiApplicationService.PatchPageCommand.builder()
                .path("prep")
                .operation(WikiPatchOperation.PREPEND)
                .content("")
                .expectedRevision(revision)
                .actor("tester")
                .build());

        String summary = service.getPageHistory("prep").get(0).getSummary();
        assertFalse(summary.contains("+0"), "prepend no-op must not show '+0': " + summary);
        assertFalse(summary.contains("-0"), "prepend no-op must not show '-0': " + summary);
    }

    @Test
    void noOpReplaceSectionSummaryNamesHeadingWithoutDeltaNoise() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Rs")
                .slug("rs")
                .content("# Rs\n\n## Status\nkeep\n")
                .kind(WikiNodeKind.PAGE)
                .build());
        String revision = service.getPage("rs").getRevision();

        service.patchPage(WikiApplicationService.PatchPageCommand.builder()
                .path("rs")
                .operation(WikiPatchOperation.REPLACE_SECTION)
                .heading("Status")
                .content("keep\n")
                .expectedRevision(revision)
                .actor("tester")
                .build());

        String summary = service.getPageHistory("rs").get(0).getSummary();
        assertFalse(summary.contains("+0"), "replace-section no-op must not show '+0': " + summary);
        assertFalse(summary.contains("-0"), "replace-section no-op must not show '-0': " + summary);
        assertTrue(summary.contains("Status"),
                "replace-section no-op should still name the target heading: " + summary);
    }

    @Test
    void createPageDoesNotIncrementAccessCount() {
        WikiApplicationService service = createService();

        WikiPage created = service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Fresh")
                .slug("fresh")
                .content("body")
                .kind(WikiNodeKind.PAGE)
                .build());

        assertEquals(0L, created.getAccessCount());
        assertTrue(service.listTopAccessed(10).stream().noneMatch(s -> "fresh".equals(s.getPath())));
    }

    @Test
    void updatePageDoesNotIncrementAccessCount() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Update Me")
                .slug("update-me")
                .content("v1")
                .kind(WikiNodeKind.PAGE)
                .build());
        String revision = service.getPage("update-me").getRevision();
        long beforeUpdate = service.listTopAccessed(10).stream()
                .filter(s -> "update-me".equals(s.getPath()))
                .findFirst().map(WikiAccessStats::getAccessCount).orElse(0L);

        service.updatePage(WikiApplicationService.UpdatePageCommand.builder()
                .path("update-me")
                .title("Updated")
                .slug("update-me")
                .content("v2")
                .expectedRevision(revision)
                .actor("tester")
                .build());

        long afterUpdate = service.listTopAccessed(10).stream()
                .filter(s -> "update-me".equals(s.getPath()))
                .findFirst().map(WikiAccessStats::getAccessCount).orElse(0L);
        assertEquals(beforeUpdate, afterUpdate);
    }

    @Test
    void patchPageDoesNotIncrementAccessCount() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Patch Me")
                .slug("patch-me")
                .content("## H\nbody\n")
                .kind(WikiNodeKind.PAGE)
                .build());
        String revision = service.getPage("patch-me").getRevision();
        long before = service.listTopAccessed(10).stream()
                .filter(s -> "patch-me".equals(s.getPath()))
                .findFirst().map(WikiAccessStats::getAccessCount).orElse(0L);

        service.patchPage(WikiApplicationService.PatchPageCommand.builder()
                .path("patch-me")
                .operation(WikiPatchOperation.APPEND)
                .content("\ntail\n")
                .expectedRevision(revision)
                .build());

        long after = service.listTopAccessed(10).stream()
                .filter(s -> "patch-me".equals(s.getPath()))
                .findFirst().map(WikiAccessStats::getAccessCount).orElse(0L);
        assertEquals(before, after);
    }

    @Test
    void deletePageRemovesAccessStats() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Doomed")
                .slug("doomed")
                .content("body")
                .kind(WikiNodeKind.PAGE)
                .build());
        service.getPage("doomed");
        service.getPage("doomed");
        assertTrue(service.listTopAccessed(10).stream().anyMatch(s -> "doomed".equals(s.getPath())));

        service.deletePage("doomed");

        assertTrue(service.listTopAccessed(10).stream().noneMatch(s -> "doomed".equals(s.getPath())));
    }

    @Test
    void deleteSectionRemovesAccessStatsForDescendants() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Sec")
                .slug("sec")
                .content("")
                .kind(WikiNodeKind.SECTION)
                .build());
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("sec")
                .title("Inside")
                .slug("inside")
                .content("body")
                .kind(WikiNodeKind.PAGE)
                .build());
        service.getPage("sec/inside");
        service.getPage("sec/inside");
        assertTrue(service.listTopAccessed(10).stream().anyMatch(s -> "sec/inside".equals(s.getPath())));

        service.deletePage("sec");

        assertTrue(service.listTopAccessed(10).stream().noneMatch(s -> "sec/inside".equals(s.getPath())));
    }

    @Test
    void graphSummaryIgnoresLinksInsideFencedCodeBlocks() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("CodeSample")
                .slug("code-sample")
                .content("Intro\n\n```markdown\n[phantom](missing-target)\n```\n")
                .kind(WikiNodeKind.PAGE)
                .build());

        assertTrue(service.getGraphSummary().getDangling().stream()
                .noneMatch(item -> "missing-target".equals(item.getToPath())));
    }

    @Test
    void frontmatterRoundTripsEscapedQuoteInTag() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Tricky")
                .slug("tricky")
                .tags(List.of("quote\"inside"))
                .summary("with \"quotes\" and \\ backslash")
                .content("body")
                .kind(WikiNodeKind.PAGE)
                .build());

        WikiPage page = service.getPage("tricky");
        assertTrue(page.getTags().contains("quote\"inside"), "tags roundtrip: " + page.getTags());
        assertEquals("with \"quotes\" and \\ backslash", page.getSummary());
    }

    @Test
    void listTopAccessedRespectsRecordAccessOnPageReadsOnly() {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Section")
                .slug("sec")
                .content("")
                .kind(WikiNodeKind.SECTION)
                .build());
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("sec")
                .title("Page")
                .slug("page")
                .content("body")
                .kind(WikiNodeKind.PAGE)
                .build());

        service.getPage("sec/page");
        service.getPage("sec/page");
        service.getPage("sec");

        List<WikiAccessStats> top = service.listTopAccessed(5);
        assertFalse(top.isEmpty());
        assertEquals("sec/page", top.get(0).getPath());
        assertEquals(2L, top.get(0).getAccessCount());
        assertTrue(top.stream().noneMatch(s -> "sec".equals(s.getPath())));
    }

    // Per-space withSpaceLock serializes these calls, so this test does not measure
    // parallelism — it proves that contending workers never lose writes or throw.
    @Test
    void concurrentCreatePagesOnSameSpaceAllSucceed() throws Exception {
        WikiApplicationService service = createService();
        String spaceId = SpaceContextHolder.require();
        int threads = 8;
        int pagesPerThread = 5;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicReference<Exception> failure = new AtomicReference<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int t = 0; t < threads; t++) {
                final int threadIndex = t;
                pool.submit(() -> {
                    try {
                        SpaceContextHolder.set(spaceId);
                        start.await();
                        for (int i = 0; i < pagesPerThread; i++) {
                            String slug = "p-" + threadIndex + "-" + i;
                            service.createPage(WikiApplicationService.CreatePageCommand.builder()
                                    .parentPath("")
                                    .title(slug)
                                    .slug(slug)
                                    .content("body")
                                    .kind(WikiNodeKind.PAGE)
                                    .build());
                        }
                    } catch (Exception ex) {
                        failure.compareAndSet(null, ex);
                    } finally {
                        SpaceContextHolder.clear();
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "workers should finish in time");
            pool.shutdownNow();
        }
        assertNull(failure.get(), () -> "concurrent createPage raised: " + failure.get());
        SpaceContextHolder.set(spaceId);
        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < pagesPerThread; i++) {
                String slug = "p-" + t + "-" + i;
                assertNotNull(service.getPage(slug), "missing page after concurrent writes: " + slug);
            }
        }
    }

    @Test
    void listTopAccessedFiltersOrphanEntries() {
        ServiceWithPort bundle = createServiceWithPort();
        WikiApplicationService service = bundle.service();

        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Real")
                .slug("real")
                .content("body")
                .kind(WikiNodeKind.PAGE)
                .build());
        service.getPage("real");
        service.getPage("real");

        bundle.port().recordAccess(bundle.spaceId(), "ghost/page");
        bundle.port().recordAccess(bundle.spaceId(), "ghost/page");
        bundle.port().recordAccess(bundle.spaceId(), "ghost/page");

        List<WikiAccessStats> top = service.listTopAccessed(10);
        assertTrue(top.stream().noneMatch(s -> "ghost/page".equals(s.getPath())),
                "orphan stat entry should be filtered out: " + top);
        assertTrue(top.stream().anyMatch(s -> "real".equals(s.getPath())));
        assertTrue(bundle.port().getStats(bundle.spaceId(), "ghost/page").isEmpty(),
                "orphan stat should also be GC'd from the port, not just filtered from the response");
    }

    private static byte[] singleEntryZip(String entryName, String content) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return buffer.toByteArray();
    }

    private WikiApplicationService createService() {
        return createServiceWithPort().service;
    }

    private ServiceWithPort createServiceWithPort() {
        WikiProperties properties = new WikiProperties();
        properties.setStorageRoot(tempDir.resolve("wiki"));
        properties.setSeedDemoContent(false);
        FileSpaceRepository spaceRepository = new FileSpaceRepository(properties);
        spaceRepository.initialize();
        Space defaultSpace = spaceRepository.findBySlug(properties.getDefaultSpaceSlug()).orElseThrow();
        SpaceContextHolder.set(defaultSpace.getId());
        FileSystemWikiRepository repository = new FileSystemWikiRepository(properties, spaceRepository);
        repository.initialize();
        WikiIndexingService indexingService = new WikiIndexingService(
                repository,
                new LuceneWikiFullTextIndexAdapter(properties),
                new SqliteWikiEmbeddingIndexAdapter(properties),
                new InMemoryLlmSettingsRepository(),
                new NoopEmbeddingPort(),
                Runnable::run);
        InMemoryWikiAccessStatsPort port = new InMemoryWikiAccessStatsPort();
        WikiApplicationService service = new WikiApplicationService(repository, properties, indexingService, port);
        return new ServiceWithPort(service, port, defaultSpace.getId());
    }

    private record ServiceWithPort(WikiApplicationService service, InMemoryWikiAccessStatsPort port, String spaceId) {
    }

    private static final class NoopEmbeddingPort implements LlmEmbeddingPort {

        @Override
        public LlmEmbeddingResponse embed(LlmEmbeddingRequest request) {
            return LlmEmbeddingResponse.builder().embeddings(List.of()).build();
        }
    }

    private static final class InMemoryLlmSettingsRepository implements LlmSettingsRepository {
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

}
