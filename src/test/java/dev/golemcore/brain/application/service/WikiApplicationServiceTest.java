package dev.golemcore.brain.application.service;

import dev.golemcore.brain.adapter.out.filesystem.FileSystemWikiRepository;
import dev.golemcore.brain.adapter.out.filesystem.space.FileSpaceRepository;
import dev.golemcore.brain.application.exception.WikiNotFoundException;
import dev.golemcore.brain.application.space.SpaceContextHolder;
import dev.golemcore.brain.config.WikiProperties;
import dev.golemcore.brain.domain.space.Space;
import org.junit.jupiter.api.AfterEach;
import dev.golemcore.brain.domain.WikiAsset;
import dev.golemcore.brain.domain.WikiAssetContent;
import dev.golemcore.brain.domain.WikiLinkStatus;
import dev.golemcore.brain.domain.WikiNodeKind;
import dev.golemcore.brain.domain.WikiPage;
import dev.golemcore.brain.domain.WikiPathLookupResult;
import dev.golemcore.brain.domain.WikiTreeNode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
        assertEquals(1, service.listAssets("operations/release-guide").size());

        service.updatePage(WikiApplicationService.UpdatePageCommand.builder()
                .path("operations/release-guide")
                .title("Release guide")
                .slug("release-guide")
                .content("![notes.txt](" + uploadedAsset.getPath() + ")")
                .build());
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
                .contains("/api/assets?path=release-guide-assets-copy&name=renamed-notes.txt"));

        WikiPage assetMove = service.movePage(WikiApplicationService.MovePageCommand.builder()
                .path(assetCopy.getPath())
                .targetParentPath("operations")
                .targetSlug("release-guide-assets-moved")
                .build());
        assertEquals(1, service.listAssets(assetMove.getPath()).size());
        assertTrue(service.getPage(assetMove.getPath()).getContent()
                .contains("/api/assets?path=operations/release-guide-assets-moved&name=renamed-notes.txt"));

        WikiAssetContent assetContent = service.openAsset("operations/release-guide", renamedAsset.getName());
        assertEquals("renamed-notes.txt", assetContent.getName());
        assertEquals("asset body", new String(assetContent.getInputStream().readAllBytes(), StandardCharsets.UTF_8));

        service.deleteAsset("operations/release-guide", renamedAsset.getName());
        assertEquals(0, service.listAssets("operations/release-guide").size());

        service.deletePage("operations/release-guide-clone");
        assertThrows(WikiNotFoundException.class, () -> service.getPage("operations/release-guide-clone"));
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
    void shouldRejectPathTraversal() {
        WikiApplicationService service = createService();
        assertThrows(IllegalArgumentException.class, () -> service.getPage("../etc/passwd"));
    }

    private WikiApplicationService createService() {
        WikiProperties properties = new WikiProperties();
        properties.setStorageRoot(tempDir.resolve("wiki"));
        properties.setSeedDemoContent(false);
        FileSpaceRepository spaceRepository = new FileSpaceRepository(properties);
        spaceRepository.initialize();
        Space defaultSpace = spaceRepository.findBySlug(properties.getDefaultSpaceSlug()).orElseThrow();
        SpaceContextHolder.set(defaultSpace.getId());
        FileSystemWikiRepository repository = new FileSystemWikiRepository(properties, spaceRepository);
        repository.initialize();
        WikiApplicationService service = new WikiApplicationService(repository, properties);
        assertTrue(Files.exists(
                properties.getStorageRoot().resolve("spaces").resolve(defaultSpace.getId()).resolve("index.md")));
        return service;
    }
}
