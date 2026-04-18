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

import me.golemcore.brain.adapter.out.filesystem.FileSystemWikiRepository;
import me.golemcore.brain.adapter.out.filesystem.space.FileSpaceRepository;
import me.golemcore.brain.adapter.out.index.lucene.LuceneWikiFullTextIndexAdapter;
import me.golemcore.brain.adapter.out.index.sqlite.SqliteWikiEmbeddingIndexAdapter;
import me.golemcore.brain.application.exception.WikiNotFoundException;
import me.golemcore.brain.application.port.out.LlmEmbeddingPort;
import me.golemcore.brain.application.port.out.LlmSettingsRepository;
import me.golemcore.brain.application.service.index.WikiIndexingService;
import me.golemcore.brain.application.service.support.InMemoryWikiAccessStatsPort;
import me.golemcore.brain.application.space.SpaceContextHolder;
import me.golemcore.brain.config.WikiProperties;
import me.golemcore.brain.domain.WikiAsset;
import me.golemcore.brain.domain.WikiAssetContent;
import me.golemcore.brain.domain.WikiLinkStatus;
import me.golemcore.brain.domain.WikiNodeKind;
import me.golemcore.brain.domain.WikiNodeReference;
import me.golemcore.brain.domain.WikiPage;
import me.golemcore.brain.domain.WikiPathLookupResult;
import me.golemcore.brain.domain.WikiIndexedDocument;
import me.golemcore.brain.domain.WikiTreeNode;
import me.golemcore.brain.domain.llm.LlmEmbeddingRequest;
import me.golemcore.brain.domain.llm.LlmEmbeddingResponse;
import me.golemcore.brain.domain.llm.LlmSettings;
import me.golemcore.brain.domain.space.Space;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiApplicationServiceTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        SpaceContextHolder.clear();
    }

    @Test
    void shouldExposeImageVersionInConfig() {
        WikiProperties properties = new WikiProperties();
        properties.setStorageRoot(tempDir.resolve("version-wiki"));
        properties.setSeedDemoContent(false);
        properties.setImageVersion("2026.04.14-test");
        WikiApplicationService service = createService(properties);

        assertEquals("2026.04.14-test", service.getConfig().getImageVersion());
    }

    @Test
    void shouldCreateLoadSearchMoveCopyDeleteLinksLookupEnsureAndAssets() throws Exception {
        WikiApplicationService service = createService();

        WikiPage createdSection = service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Operations")
                .slug("operations")
                .content("Operational procedures")
                .kind(WikiNodeKind.SECTION)
                .build());
        assertEquals("operations", createdSection.getPath());
        assertEquals(WikiNodeKind.SECTION, createdSection.getKind());

        WikiPage createdPage = service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("operations")
                .title("Deployments")
                .slug("deployments")
                .content("See [Checklist](../shared/checklist) and [Missing](../shared/missing)")
                .kind(WikiNodeKind.PAGE)
                .build());
        assertEquals("operations/deployments", createdPage.getPath());

        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Shared")
                .slug("shared")
                .content("Shared docs")
                .kind(WikiNodeKind.SECTION)
                .build());
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("shared")
                .title("Checklist")
                .slug("checklist")
                .content("Release checklist")
                .kind(WikiNodeKind.PAGE)
                .build());

        WikiPage updatedPage = service.updatePage(WikiApplicationService.UpdatePageCommand.builder()
                .path("operations/deployments")
                .title("Release guide")
                .slug("release-guide")
                .content(
                        "Updated release checklist with [Checklist](../shared/checklist) and [Missing](../shared/missing)")
                .build());
        assertEquals("operations/release-guide", updatedPage.getPath());

        WikiPage ensuredPage = service.ensurePage("operations/generated-page", "Generated Page");
        assertEquals("operations/generated-page", ensuredPage.getPath());

        WikiPathLookupResult lookupResult = service.lookupPath("operations/release-runbook");
        assertFalse(lookupResult.isExists());
        assertEquals(2, lookupResult.getSegments().size());

        assertFalse(service.search("nonexistent-query").iterator().hasNext());
        assertTrue(
                service.search("release").stream().anyMatch(hit -> "operations/release-guide".equals(hit.getPath())));

        WikiPage copiedPage = service.copyPage(WikiApplicationService.CopyPageCommand.builder()
                .path("operations/release-guide")
                .targetParentPath("")
                .targetSlug("release-guide-copy")
                .build());
        assertEquals("release-guide-copy", copiedPage.getPath());

        WikiPage movedPage = service.movePage(WikiApplicationService.MovePageCommand.builder()
                .path("release-guide-copy")
                .targetParentPath("operations")
                .targetSlug("release-guide-clone")
                .build());
        assertEquals("operations/release-guide-clone", movedPage.getPath());

        service.sortChildren(WikiApplicationService.SortChildrenCommand.builder()
                .path("operations")
                .orderedSlugs(List.of("release-guide-clone", "release-guide"))
                .build());
        WikiTreeNode operationsTree = service.getTree().getChildren().stream()
                .filter(node -> "operations".equals(node.getPath()))
                .findFirst()
                .orElseThrow();
        assertEquals("release-guide-clone", operationsTree.getChildren().getFirst().getSlug());

        WikiLinkStatus linkStatus = service.getLinkStatus("operations/release-guide");
        assertEquals(1, linkStatus.getOutgoings().size());
        assertEquals(1, linkStatus.getBrokenOutgoings().size());
        assertTrue(linkStatus.getBrokenOutgoings().stream().anyMatch(item -> item.isBroken()));

        WikiAsset uploadedAsset = service.uploadAsset(
                "operations/release-guide",
                "notes.txt",
                "text/plain",
                new ByteArrayInputStream("asset body".getBytes(StandardCharsets.UTF_8)));
        assertEquals("text/plain", uploadedAsset.getContentType());
        assertEquals("/api/spaces/default/assets?path=operations%2Frelease-guide&name=" + uploadedAsset.getName(),
                uploadedAsset.getPath());
        assertEquals(1, service.listAssets("operations/release-guide").size());

        service.updatePage(WikiApplicationService.UpdatePageCommand.builder()
                .path("operations/release-guide")
                .title("Release guide")
                .slug("release-guide")
                .content("![notes.txt](" + uploadedAsset.getPath() + ")")
                .build());
        WikiLinkStatus assetLinkStatus = service.getLinkStatus("operations/release-guide");
        assertTrue(assetLinkStatus.getOutgoings().isEmpty());
        assertTrue(assetLinkStatus.getBrokenOutgoings().isEmpty());

        WikiAsset renamedAsset = service.renameAsset("operations/release-guide", uploadedAsset.getName(),
                "renamed-notes.txt");
        assertEquals("renamed-notes.txt", renamedAsset.getName());
        assertTrue(service.getPage("operations/release-guide").getContent().contains(renamedAsset.getPath()));
        assertFalse(service.getPage("operations/release-guide").getContent().contains(uploadedAsset.getPath()));

        WikiPage assetCopy = service.copyPage(WikiApplicationService.CopyPageCommand.builder()
                .path("operations/release-guide")
                .targetParentPath("")
                .targetSlug("release-guide-assets-copy")
                .build());
        assertEquals(1, service.listAssets(assetCopy.getPath()).size());
        assertTrue(service.getPage(assetCopy.getPath()).getContent()
                .contains("/api/spaces/default/assets?path=release-guide-assets-copy&name=renamed-notes.txt"));

        WikiPage assetMove = service.movePage(WikiApplicationService.MovePageCommand.builder()
                .path(assetCopy.getPath())
                .targetParentPath("operations")
                .targetSlug("release-guide-assets-moved")
                .build());
        assertEquals(1, service.listAssets(assetMove.getPath()).size());
        assertTrue(service.getPage(assetMove.getPath()).getContent()
                .contains(
                        "/api/spaces/default/assets?path=operations%2Frelease-guide-assets-moved&name=renamed-notes.txt"));

        WikiAssetContent assetContent = service.openAsset("operations/release-guide", renamedAsset.getName());
        assertEquals("renamed-notes.txt", assetContent.getName());
        assertEquals("asset body", new String(assetContent.getInputStream().readAllBytes(), StandardCharsets.UTF_8));

        service.deleteAsset("operations/release-guide", renamedAsset.getName());
        assertEquals(0, service.listAssets("operations/release-guide").size());

        service.deletePage("operations/release-guide-clone");
        assertThrows(WikiNotFoundException.class, () -> service.getPage("operations/release-guide-clone"));
    }

    @Test
    void shouldTreatDirectoryWithoutIndexAsSyntheticSectionUntilExplicitlyUpdated() throws Exception {
        WikiProperties properties = new WikiProperties();
        properties.setStorageRoot(tempDir.resolve("manual-section-wiki"));
        properties.setSeedDemoContent(false);
        FileSpaceRepository spaceRepository = new FileSpaceRepository(properties);
        spaceRepository.initialize();
        Space defaultSpace = spaceRepository.findBySlug(properties.getDefaultSpaceSlug()).orElseThrow();
        SpaceContextHolder.set(defaultSpace.getId());
        FileSystemWikiRepository repository = new FileSystemWikiRepository(properties, spaceRepository);
        repository.initialize();
        Path sectionPath = properties.getStorageRoot().resolve("spaces").resolve(defaultSpace.getId())
                .resolve("manual-notes");
        Path sectionIndexPath = sectionPath.resolve("index.md");
        Files.createDirectories(sectionPath);
        WikiApplicationService service = new WikiApplicationService(
                repository,
                properties,
                new WikiIndexingService(
                        repository,
                        new LuceneWikiFullTextIndexAdapter(properties),
                        new SqliteWikiEmbeddingIndexAdapter(properties),
                        new InMemoryLlmSettingsRepository(),
                        new NoopEmbeddingPort(),
                        Runnable::run),
                new InMemoryWikiAccessStatsPort());

        WikiPage syntheticSection = service.getPage("manual-notes");

        assertEquals(WikiNodeKind.SECTION, syntheticSection.getKind());
        assertEquals("Manual Notes", syntheticSection.getTitle());
        assertEquals("", syntheticSection.getContent());
        assertFalse(Files.exists(sectionIndexPath));

        WikiPage createdPage = service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("manual-notes")
                .title("Imported Note")
                .slug("imported-note")
                .content("Created under manually provisioned section")
                .kind(WikiNodeKind.PAGE)
                .build());

        assertEquals("manual-notes/imported-note", createdPage.getPath());
        assertFalse(Files.exists(sectionIndexPath));

        WikiPage updatedSection = service.updatePage(WikiApplicationService.UpdatePageCommand.builder()
                .path("manual-notes")
                .title("Manual Notes")
                .slug("manual-notes")
                .content("Landing content")
                .build());

        assertEquals("manual-notes", updatedSection.getPath());
        assertEquals("Landing content", updatedSection.getContent());
        assertTrue(Files.exists(sectionIndexPath));
    }

    @Test
    void shouldConvertBetweenPageAndEmptySectionWhileKeepingAssets() throws Exception {
        WikiApplicationService service = createService();
        service.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath("")
                .title("Scratch")
                .slug("scratch")
                .content("Scratch content")
                .kind(WikiNodeKind.PAGE)
                .build());
        service.uploadAsset(
                "scratch",
                "diagram.png",
                "image/png",
                new ByteArrayInputStream("png".getBytes(StandardCharsets.UTF_8)));

        WikiPage section = service.convertPage(WikiApplicationService.ConvertPageCommand.builder()
                .path("scratch")
                .targetKind(WikiNodeKind.SECTION)
                .build());
        assertEquals(WikiNodeKind.SECTION, section.getKind());
        assertEquals(1, service.listAssets("scratch").size());

        WikiPage page = service.convertPage(WikiApplicationService.ConvertPageCommand.builder()
                .path("scratch")
                .targetKind(WikiNodeKind.PAGE)
                .build());
        assertEquals(WikiNodeKind.PAGE, page.getKind());
        assertEquals(1, service.listAssets("scratch").size());
        assertEquals("diagram.png", service.openAsset("scratch", "diagram.png").getName());
    }

    @Test
    void shouldListIndexedDocumentsForRequestedSpaceOnly() {
        WikiProperties properties = new WikiProperties();
        properties.setStorageRoot(tempDir.resolve("wiki"));
        properties.setSeedDemoContent(false);
        FileSpaceRepository spaceRepository = new FileSpaceRepository(properties);
        spaceRepository.initialize();
        Space defaultSpace = spaceRepository.findBySlug(properties.getDefaultSpaceSlug()).orElseThrow();
        Space extraSpace = Space.builder()
                .id("extra-space")
                .slug("extra")
                .name("Extra")
                .createdAt(Instant.parse("2026-04-11T00:00:00Z"))
                .build();
        spaceRepository.save(extraSpace);
        FileSystemWikiRepository repository = new FileSystemWikiRepository(properties, spaceRepository);
        repository.initialize();

        SpaceContextHolder.set(defaultSpace.getId());
        repository.createPage("", "Default Only", "shared", "default body", WikiNodeKind.PAGE);
        SpaceContextHolder.set(extraSpace.getId());
        repository.createPage("", "Extra Only", "shared", "extra body", WikiNodeKind.PAGE);

        List<String> defaultTitles = repository.listDocuments(defaultSpace.getId()).stream()
                .map(WikiIndexedDocument::getTitle)
                .toList();
        List<String> extraTitles = repository.listDocuments(extraSpace.getId()).stream()
                .map(WikiIndexedDocument::getTitle)
                .toList();

        assertTrue(defaultTitles.contains("Default Only"));
        assertFalse(defaultTitles.contains("Extra Only"));
        assertTrue(extraTitles.contains("Extra Only"));
        assertFalse(extraTitles.contains("Default Only"));
        assertEquals(extraSpace.getId(), SpaceContextHolder.get());
    }

    @Test
    void shouldRejectSyntheticSectionReferenceOutsideSpaceRoot() {
        WikiProperties properties = new WikiProperties();
        properties.setStorageRoot(tempDir.resolve("wiki-security"));
        properties.setSeedDemoContent(false);
        FileSpaceRepository spaceRepository = new FileSpaceRepository(properties);
        spaceRepository.initialize();
        Space defaultSpace = spaceRepository.findBySlug(properties.getDefaultSpaceSlug()).orElseThrow();
        SpaceContextHolder.set(defaultSpace.getId());
        FileSystemWikiRepository repository = new FileSystemWikiRepository(properties, spaceRepository);
        repository.initialize();
        WikiNodeReference maliciousReference = WikiNodeReference.builder()
                .id("evil")
                .path("evil")
                .parentPath("")
                .slug("evil")
                .kind(WikiNodeKind.SECTION)
                .nodePath(properties.getStorageRoot().resolve("outside"))
                .parentDirectory(properties.getStorageRoot())
                .markdownPath(properties.getStorageRoot().resolve("outside").resolve("index.md"))
                .build();

        assertThrows(IllegalArgumentException.class, () -> repository.readDocument(maliciousReference));
    }

    @Test
    void shouldRejectPathTraversal() {
        WikiApplicationService service = createService();
        assertThrows(IllegalArgumentException.class, () -> service.getPage("../etc/passwd"));
    }

    private WikiApplicationService createService() {
        WikiProperties properties = new WikiProperties();
        properties.setStorageRoot(tempDir.resolve("wiki"));
        properties.setSeedDemoContent(false);
        return createService(properties);
    }

    private WikiApplicationService createService(WikiProperties properties) {
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
        WikiApplicationService service = new WikiApplicationService(
                repository, properties, indexingService, new InMemoryWikiAccessStatsPort());
        assertTrue(Files.exists(
                properties.getStorageRoot().resolve("spaces").resolve(defaultSpace.getId()).resolve("index.md")));
        return service;
    }

    private static class NoopEmbeddingPort implements LlmEmbeddingPort {

        @Override
        public LlmEmbeddingResponse embed(LlmEmbeddingRequest request) {
            return LlmEmbeddingResponse.builder()
                    .embeddings(List.of())
                    .build();
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

}
