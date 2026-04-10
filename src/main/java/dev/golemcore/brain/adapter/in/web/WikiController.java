package dev.golemcore.brain.adapter.in.web;

import dev.golemcore.brain.adapter.in.web.auth.AuthCookieHelper;
import dev.golemcore.brain.adapter.in.web.dto.CopyPagePayload;
import dev.golemcore.brain.adapter.in.web.dto.CreatePagePayload;
import dev.golemcore.brain.adapter.in.web.dto.EnsurePagePayload;
import dev.golemcore.brain.adapter.in.web.dto.MovePagePayload;
import dev.golemcore.brain.adapter.in.web.dto.RenameAssetPayload;
import dev.golemcore.brain.adapter.in.web.dto.SortChildrenPayload;
import dev.golemcore.brain.adapter.in.web.dto.UpdatePagePayload;
import dev.golemcore.brain.application.service.WikiApplicationService;
import dev.golemcore.brain.application.service.auth.AuthService;
import dev.golemcore.brain.domain.WikiAsset;
import dev.golemcore.brain.domain.WikiAssetContent;
import dev.golemcore.brain.domain.WikiConfigResponse;
import dev.golemcore.brain.domain.WikiImportApplyResponse;
import dev.golemcore.brain.domain.WikiImportPlanResponse;
import dev.golemcore.brain.domain.WikiLinkStatus;
import dev.golemcore.brain.domain.WikiPage;
import dev.golemcore.brain.domain.WikiPageHistoryEntry;
import dev.golemcore.brain.domain.WikiPathLookupResult;
import dev.golemcore.brain.domain.WikiSearchHit;
import dev.golemcore.brain.domain.WikiSearchStatus;
import dev.golemcore.brain.domain.WikiTreeNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WikiController {

    private final WikiApplicationService wikiApplicationService;
    private final AuthService authService;
    private final AuthCookieHelper authCookieHelper;

    @GetMapping("/config")
    public WikiConfigResponse getConfig() {
        return wikiApplicationService.getConfig();
    }

    @GetMapping("/tree")
    public WikiTreeNode getTree() {
        return wikiApplicationService.getTree();
    }

    @GetMapping("/page")
    public WikiPage getPage(@RequestParam(name = "path", defaultValue = "") String path) {
        return wikiApplicationService.getPage(path);
    }

    @GetMapping("/pages/by-path")
    public WikiPage getPageByPath(@RequestParam(name = "path", defaultValue = "") String path) {
        return wikiApplicationService.getPage(path);
    }

    @GetMapping("/page/history")
    public List<WikiPageHistoryEntry> getPageHistory(@RequestParam(name = "path") String path) {
        return wikiApplicationService.getPageHistory(path);
    }

    @PostMapping("/page/history/restore")
    public WikiPage restorePageHistory(@RequestParam(name = "path") String path, @RequestParam(name = "versionId") String versionId, HttpServletRequest request) {
        authService.requireEditAccess(authCookieHelper.readSessionToken(request));
        return wikiApplicationService.restorePageHistory(path, versionId);
    }

    @PostMapping("/pages")
    public WikiPage createPage(@Valid @RequestBody CreatePagePayload payload, HttpServletRequest request) {
        authService.requireEditAccess(authCookieHelper.readSessionToken(request));
        return wikiApplicationService.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath(payload.getParentPath())
                .title(payload.getTitle())
                .slug(payload.getSlug())
                .content(payload.getContent())
                .kind(payload.getKind())
                .build());
    }

    @PostMapping("/pages/ensure")
    public WikiPage ensurePage(@Valid @RequestBody EnsurePagePayload payload, HttpServletRequest request) {
        authService.requireEditAccess(authCookieHelper.readSessionToken(request));
        return wikiApplicationService.ensurePage(payload.getPath(), payload.getTargetTitle());
    }

    @GetMapping("/pages/lookup")
    public WikiPathLookupResult lookupPath(@RequestParam(name = "path") String path) {
        return wikiApplicationService.lookupPath(path);
    }

    @PutMapping("/page")
    public WikiPage updatePage(@RequestParam(name = "path") String path, @Valid @RequestBody UpdatePagePayload payload, HttpServletRequest request) {
        authService.requireEditAccess(authCookieHelper.readSessionToken(request));
        return wikiApplicationService.updatePage(WikiApplicationService.UpdatePageCommand.builder()
                .path(path)
                .title(payload.getTitle())
                .slug(payload.getSlug())
                .content(payload.getContent())
                .build());
    }

    @DeleteMapping("/page")
    public void deletePage(@RequestParam(name = "path") String path, HttpServletRequest request) {
        authService.requireEditAccess(authCookieHelper.readSessionToken(request));
        wikiApplicationService.deletePage(path);
    }

    @PostMapping("/page/move")
    public WikiPage movePage(@RequestParam(name = "path") String path, @Valid @RequestBody MovePagePayload payload, HttpServletRequest request) {
        authService.requireEditAccess(authCookieHelper.readSessionToken(request));
        return wikiApplicationService.movePage(WikiApplicationService.MovePageCommand.builder()
                .path(path)
                .targetParentPath(payload.getTargetParentPath())
                .targetSlug(payload.getTargetSlug())
                .beforeSlug(payload.getBeforeSlug())
                .build());
    }

    @PostMapping("/page/copy")
    public WikiPage copyPage(@RequestParam(name = "path") String path, @Valid @RequestBody CopyPagePayload payload, HttpServletRequest request) {
        authService.requireEditAccess(authCookieHelper.readSessionToken(request));
        return wikiApplicationService.copyPage(WikiApplicationService.CopyPageCommand.builder()
                .path(path)
                .targetParentPath(payload.getTargetParentPath())
                .targetSlug(payload.getTargetSlug())
                .beforeSlug(payload.getBeforeSlug())
                .build());
    }

    @PutMapping("/section/sort")
    public void sortChildren(@RequestParam(name = "path") String path, @Valid @RequestBody SortChildrenPayload payload, HttpServletRequest request) {
        authService.requireEditAccess(authCookieHelper.readSessionToken(request));
        wikiApplicationService.sortChildren(WikiApplicationService.SortChildrenCommand.builder()
                .path(path)
                .orderedSlugs(payload.getOrderedSlugs())
                .build());
    }

    @GetMapping("/search")
    public List<WikiSearchHit> search(@RequestParam(name = "q", defaultValue = "") String query) {
        return wikiApplicationService.search(query);
    }

    @GetMapping("/search/status")
    public WikiSearchStatus getSearchStatus() {
        return wikiApplicationService.getSearchStatus();
    }

    @PostMapping(value = "/import/markdown/plan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WikiImportPlanResponse planMarkdownImport(@RequestPart("file") MultipartFile file) throws IOException {
        return wikiApplicationService.planMarkdownImport(file.getInputStream());
    }

    @PostMapping(value = "/import/markdown/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WikiImportApplyResponse applyMarkdownImport(@RequestPart("file") MultipartFile file) throws IOException {
        return wikiApplicationService.applyMarkdownImport(file.getInputStream());
    }

    @GetMapping("/links")
    public WikiLinkStatus getLinkStatus(@RequestParam(name = "path") String path) {
        return wikiApplicationService.getLinkStatus(path);
    }

    @GetMapping("/pages/assets")
    public List<WikiAsset> listAssets(@RequestParam(name = "path") String path, HttpServletRequest request) {
        authService.requireEditAccess(authCookieHelper.readSessionToken(request));
        return wikiApplicationService.listAssets(path);
    }

    @PostMapping(value = "/pages/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WikiAsset uploadAsset(@RequestParam(name = "path") String path, @RequestPart("file") MultipartFile file, HttpServletRequest request) throws IOException {
        authService.requireEditAccess(authCookieHelper.readSessionToken(request));
        return wikiApplicationService.uploadAsset(path, file.getOriginalFilename(), file.getContentType(), file.getInputStream());
    }

    @PutMapping("/pages/assets/rename")
    public WikiAsset renameAsset(@RequestParam(name = "path") String path, @Valid @RequestBody RenameAssetPayload payload, HttpServletRequest request) {
        authService.requireEditAccess(authCookieHelper.readSessionToken(request));
        return wikiApplicationService.renameAsset(path, payload.getOldName(), payload.getNewName());
    }

    @DeleteMapping("/pages/assets")
    public void deleteAsset(@RequestParam(name = "path") String path, @RequestParam(name = "name") String name, HttpServletRequest request) {
        authService.requireEditAccess(authCookieHelper.readSessionToken(request));
        wikiApplicationService.deleteAsset(path, name);
    }

    @GetMapping("/assets")
    public ResponseEntity<InputStreamResource> getAsset(@RequestParam(name = "path") String path, @RequestParam(name = "name") String name) {
        WikiAssetContent assetContent = wikiApplicationService.openAsset(path, name);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + assetContent.getName() + "\"")
                .contentLength(assetContent.getSize())
                .contentType(MediaType.parseMediaType(assetContent.getContentType()))
                .body(new InputStreamResource(assetContent.getInputStream()));
    }
}
