package dev.golemcore.brain.service;

import dev.golemcore.brain.config.WikiProperties;
import dev.golemcore.brain.domain.WikiNodeKind;
import dev.golemcore.brain.domain.WikiPage;
import dev.golemcore.brain.domain.WikiTreeNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateLoadSearchMoveCopyAndDeletePages() {
        WikiStorageService service = createService();

        WikiPage createdSection = service.createPage(WikiStorageService.CreatePageRequest.builder()
                .parentPath("")
                .title("Operations")
                .slug("operations")
                .content("Operational procedures")
                .kind(WikiNodeKind.SECTION)
                .build());
        assertEquals("operations", createdSection.getPath());
        assertEquals(WikiNodeKind.SECTION, createdSection.getKind());

        WikiPage createdPage = service.createPage(WikiStorageService.CreatePageRequest.builder()
                .parentPath("operations")
                .title("Deployments")
                .slug("deployments")
                .content("Release checklist and deployment notes")
                .kind(WikiNodeKind.PAGE)
                .build());
        assertEquals("operations/deployments", createdPage.getPath());

        WikiPage updatedPage = service.updatePage("operations/deployments", WikiStorageService.UpdatePageRequest.builder()
                .title("Release guide")
                .slug("release-guide")
                .content("Updated release checklist")
                .build());
        assertEquals("operations/release-guide", updatedPage.getPath());
        assertEquals("Release guide", updatedPage.getTitle());

        assertFalse(service.search("nonexistent-query").iterator().hasNext());
        assertTrue(service.search("release").stream().anyMatch(hit -> hit.getPath().equals("operations/release-guide")));

        WikiPage copiedPage = service.copyPage("operations/release-guide", WikiStorageService.CopyPageRequest.builder()
                .targetParentPath("")
                .targetSlug("release-guide-copy")
                .build());
        assertEquals("release-guide-copy", copiedPage.getPath());

        WikiPage movedPage = service.movePage("release-guide-copy", WikiStorageService.MovePageRequest.builder()
                .targetParentPath("operations")
                .targetSlug("release-guide-clone")
                .build());
        assertEquals("operations/release-guide-clone", movedPage.getPath());

        service.sortChildren("operations", WikiStorageService.SortChildrenRequest.builder()
                .orderedSlugs(List.of("release-guide-clone", "release-guide"))
                .build());
        WikiTreeNode operationsTree = service.getTree().getChildren().stream()
                .filter(node -> node.getPath().equals("operations"))
                .findFirst()
                .orElseThrow();
        assertEquals("release-guide-clone", operationsTree.getChildren().getFirst().getSlug());

        service.deletePage("operations/release-guide-clone");
        assertThrows(WikiNotFoundException.class, () -> service.getPage("operations/release-guide-clone"));
    }

    @Test
    void shouldRejectPathTraversal() {
        WikiStorageService service = createService();
        assertThrows(IllegalArgumentException.class, () -> service.getPage("../etc/passwd"));
    }

    private WikiStorageService createService() {
        WikiProperties properties = new WikiProperties();
        properties.setStorageRoot(tempDir.resolve("wiki"));
        properties.setSeedDemoContent(false);
        WikiStorageService service = new WikiStorageService(properties);
        service.initializeStorage();
        assertTrue(Files.exists(properties.getStorageRoot().resolve("index.md")));
        return service;
    }
}
