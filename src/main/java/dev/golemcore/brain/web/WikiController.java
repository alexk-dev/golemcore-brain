package dev.golemcore.brain.web;

import dev.golemcore.brain.config.WikiProperties;
import dev.golemcore.brain.domain.WikiConfigResponse;
import dev.golemcore.brain.domain.WikiPage;
import dev.golemcore.brain.domain.WikiSearchHit;
import dev.golemcore.brain.domain.WikiTreeNode;
import dev.golemcore.brain.service.WikiStorageService;
import dev.golemcore.brain.web.dto.CopyPagePayload;
import dev.golemcore.brain.web.dto.CreatePagePayload;
import dev.golemcore.brain.web.dto.MovePagePayload;
import dev.golemcore.brain.web.dto.SortChildrenPayload;
import dev.golemcore.brain.web.dto.UpdatePagePayload;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WikiController {

    private final WikiStorageService wikiStorageService;
    private final WikiProperties wikiProperties;

    @GetMapping("/config")
    public WikiConfigResponse getConfig() {
        return WikiConfigResponse.builder()
                .siteTitle(wikiProperties.getSiteTitle())
                .rootPath("")
                .build();
    }

    @GetMapping("/tree")
    public WikiTreeNode getTree() {
        return wikiStorageService.getTree();
    }

    @GetMapping("/page")
    public WikiPage getPage(@RequestParam(name = "path", defaultValue = "") String path) {
        return wikiStorageService.getPage(path);
    }

    @PostMapping("/pages")
    public WikiPage createPage(@Valid @RequestBody CreatePagePayload payload) {
        return wikiStorageService.createPage(WikiStorageService.CreatePageRequest.builder()
                .parentPath(payload.getParentPath())
                .title(payload.getTitle())
                .slug(payload.getSlug())
                .content(payload.getContent())
                .kind(payload.getKind())
                .build());
    }

    @PutMapping("/page")
    public WikiPage updatePage(@RequestParam(name = "path") String path, @Valid @RequestBody UpdatePagePayload payload) {
        return wikiStorageService.updatePage(path, WikiStorageService.UpdatePageRequest.builder()
                .title(payload.getTitle())
                .slug(payload.getSlug())
                .content(payload.getContent())
                .build());
    }

    @DeleteMapping("/page")
    public void deletePage(@RequestParam(name = "path") String path) {
        wikiStorageService.deletePage(path);
    }

    @PostMapping("/page/move")
    public WikiPage movePage(@RequestParam(name = "path") String path, @Valid @RequestBody MovePagePayload payload) {
        return wikiStorageService.movePage(path, WikiStorageService.MovePageRequest.builder()
                .targetParentPath(payload.getTargetParentPath())
                .targetSlug(payload.getTargetSlug())
                .beforeSlug(payload.getBeforeSlug())
                .build());
    }

    @PostMapping("/page/copy")
    public WikiPage copyPage(@RequestParam(name = "path") String path, @Valid @RequestBody CopyPagePayload payload) {
        return wikiStorageService.copyPage(path, WikiStorageService.CopyPageRequest.builder()
                .targetParentPath(payload.getTargetParentPath())
                .targetSlug(payload.getTargetSlug())
                .beforeSlug(payload.getBeforeSlug())
                .build());
    }

    @PutMapping("/section/sort")
    public void sortChildren(@RequestParam(name = "path") String path, @Valid @RequestBody SortChildrenPayload payload) {
        wikiStorageService.sortChildren(path, WikiStorageService.SortChildrenRequest.builder()
                .orderedSlugs(payload.getOrderedSlugs())
                .build());
    }

    @GetMapping("/search")
    public List<WikiSearchHit> search(@RequestParam(name = "q", defaultValue = "") String query) {
        return wikiStorageService.search(query);
    }
}
