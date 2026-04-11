package dev.golemcore.brain.adapter.in.web;

import dev.golemcore.brain.adapter.in.web.auth.AuthContextResolver;
import dev.golemcore.brain.adapter.in.web.dto.ConvertPagePayload;
import dev.golemcore.brain.adapter.in.web.dto.CopyPagePayload;
import dev.golemcore.brain.adapter.in.web.dto.CreatePagePayload;
import dev.golemcore.brain.adapter.in.web.dto.EnsurePagePayload;
import dev.golemcore.brain.adapter.in.web.dto.MarkdownImportOptionsPayload;
import dev.golemcore.brain.adapter.in.web.dto.MovePagePayload;
import dev.golemcore.brain.adapter.in.web.dto.RenameAssetPayload;
import dev.golemcore.brain.adapter.in.web.dto.SemanticSearchPayload;
import dev.golemcore.brain.adapter.in.web.dto.SortChildrenPayload;
import dev.golemcore.brain.adapter.in.web.dto.UpdatePagePayload;
import dev.golemcore.brain.application.service.WikiApplicationService;
import dev.golemcore.brain.domain.WikiAsset;
import dev.golemcore.brain.domain.WikiAssetContent;
import dev.golemcore.brain.domain.WikiImportApplyResponse;
import dev.golemcore.brain.domain.WikiImportPlanResponse;
import dev.golemcore.brain.domain.WikiLinkStatus;
import dev.golemcore.brain.domain.WikiPage;
import dev.golemcore.brain.domain.WikiPageHistoryEntry;
import dev.golemcore.brain.domain.WikiPageHistoryVersion;
import dev.golemcore.brain.domain.WikiPathLookupResult;
import dev.golemcore.brain.domain.WikiSearchHit;
import dev.golemcore.brain.domain.WikiSemanticSearchResult;
import dev.golemcore.brain.domain.WikiSearchStatus;
import dev.golemcore.brain.domain.WikiTreeNode;
import dev.golemcore.brain.domain.auth.AuthContext;
import dev.golemcore.brain.domain.auth.UserRole;
import dev.golemcore.brain.web.SpaceResolverFilter;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/spaces/{slug}")
@RequiredArgsConstructor
public class WikiController {

    private final WikiApplicationService wikiApplicationService;
    private final AuthContextResolver authContextResolver;

    @GetMapping("/tree")
    public WikiTreeNode getTree(@PathVariable String slug, HttpServletRequest request) {
        requireView(request);
        return wikiApplicationService.getTree();
    }

    @GetMapping("/page")
    public WikiPage getPage(@PathVariable String slug, @RequestParam(name = "path", defaultValue = "") String path,
            HttpServletRequest request) {
        requireView(request);
        return wikiApplicationService.getPage(path);
    }

    @GetMapping("/pages/by-path")
    public WikiPage getPageByPath(@PathVariable String slug,
            @RequestParam(name = "path", defaultValue = "") String path, HttpServletRequest request) {
        requireView(request);
        return wikiApplicationService.getPage(path);
    }

    @GetMapping("/page/history")
    public List<WikiPageHistoryEntry> getPageHistory(@PathVariable String slug,
            @RequestParam(name = "path") String path, HttpServletRequest request) {
        requireView(request);
        return wikiApplicationService.getPageHistory(path);
    }

    @GetMapping("/page/history/version")
    public WikiPageHistoryVersion getPageHistoryVersion(@PathVariable String slug,
            @RequestParam(name = "path") String path, @RequestParam(name = "versionId") String versionId,
            HttpServletRequest request) {
        requireView(request);
        return wikiApplicationService.getPageHistoryVersion(path, versionId);
    }

    @PostMapping("/page/history/restore")
    public WikiPage restorePageHistory(@PathVariable String slug, @RequestParam(name = "path") String path,
            @RequestParam(name = "versionId") String versionId, HttpServletRequest request) {
        AuthContext context = requireEdit(request);
        return wikiApplicationService.restorePageHistory(path, versionId, resolveActor(context));
    }

    @PostMapping("/pages")
    public WikiPage createPage(@PathVariable String slug, @Valid @RequestBody CreatePagePayload payload,
            HttpServletRequest request) {
        requireEdit(request);
        return wikiApplicationService.createPage(WikiApplicationService.CreatePageCommand.builder()
                .parentPath(payload.getParentPath())
                .title(payload.getTitle())
                .slug(payload.getSlug())
                .content(payload.getContent())
                .kind(payload.getKind())
                .build());
    }

    @PostMapping("/pages/ensure")
    public WikiPage ensurePage(@PathVariable String slug, @Valid @RequestBody EnsurePagePayload payload,
            HttpServletRequest request) {
        requireEdit(request);
        return wikiApplicationService.ensurePage(payload.getPath(), payload.getTargetTitle());
    }

    @GetMapping("/pages/lookup")
    public WikiPathLookupResult lookupPath(@PathVariable String slug, @RequestParam(name = "path") String path,
            HttpServletRequest request) {
        requireView(request);
        return wikiApplicationService.lookupPath(path);
    }

    @PutMapping("/page")
    public WikiPage updatePage(@PathVariable String slug, @RequestParam(name = "path") String path,
            @Valid @RequestBody UpdatePagePayload payload, HttpServletRequest request) {
        AuthContext context = requireEdit(request);
        return wikiApplicationService.updatePage(WikiApplicationService.UpdatePageCommand.builder()
                .path(path)
                .title(payload.getTitle())
                .slug(payload.getSlug())
                .content(payload.getContent())
                .expectedRevision(payload.getRevision())
                .actor(resolveActor(context))
                .build());
    }

    @DeleteMapping("/page")
    public void deletePage(@PathVariable String slug, @RequestParam(name = "path") String path,
            HttpServletRequest request) {
        requireEdit(request);
        wikiApplicationService.deletePage(path);
    }

    @PostMapping("/page/move")
    public WikiPage movePage(@PathVariable String slug, @RequestParam(name = "path") String path,
            @Valid @RequestBody MovePagePayload payload, HttpServletRequest request) {
        requireEdit(request);
        return wikiApplicationService.movePage(WikiApplicationService.MovePageCommand.builder()
                .path(path)
                .targetParentPath(payload.getTargetParentPath())
                .targetSlug(payload.getTargetSlug())
                .beforeSlug(payload.getBeforeSlug())
                .build());
    }

    @PostMapping("/page/copy")
    public WikiPage copyPage(@PathVariable String slug, @RequestParam(name = "path") String path,
            @Valid @RequestBody CopyPagePayload payload, HttpServletRequest request) {
        requireEdit(request);
        return wikiApplicationService.copyPage(WikiApplicationService.CopyPageCommand.builder()
                .path(path)
                .targetParentPath(payload.getTargetParentPath())
                .targetSlug(payload.getTargetSlug())
                .beforeSlug(payload.getBeforeSlug())
                .build());
    }

    @PostMapping("/page/convert")
    public WikiPage convertPage(@PathVariable String slug, @RequestParam(name = "path") String path,
            @Valid @RequestBody ConvertPagePayload payload, HttpServletRequest request) {
        requireEdit(request);
        return wikiApplicationService.convertPage(WikiApplicationService.ConvertPageCommand.builder()
                .path(path)
                .targetKind(payload.getTargetKind())
                .build());
    }

    @PutMapping("/section/sort")
    public void sortChildren(@PathVariable String slug, @RequestParam(name = "path") String path,
            @Valid @RequestBody SortChildrenPayload payload, HttpServletRequest request) {
        requireEdit(request);
        wikiApplicationService.sortChildren(WikiApplicationService.SortChildrenCommand.builder()
                .path(path)
                .orderedSlugs(payload.getOrderedSlugs())
                .build());
    }

    @GetMapping("/search")
    public List<WikiSearchHit> search(@PathVariable String slug,
            @RequestParam(name = "q", defaultValue = "") String query, HttpServletRequest request) {
        requireView(request);
        return wikiApplicationService.search(query);
    }

    @GetMapping("/search/status")
    public WikiSearchStatus getSearchStatus(@PathVariable String slug, HttpServletRequest request) {
        requireView(request);
        return wikiApplicationService.getSearchStatus();
    }

    @PostMapping("/search/semantic")
    public WikiSemanticSearchResult semanticSearch(@PathVariable String slug,
            @Valid @RequestBody SemanticSearchPayload payload, HttpServletRequest request) {
        requireView(request);
        return wikiApplicationService.semanticSearch(payload.getQuery());
    }

    @PostMapping(value = "/import/markdown/plan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WikiImportPlanResponse planMarkdownImport(
            @PathVariable String slug,
            @RequestPart("file") MultipartFile file,
            @RequestPart(name = "options", required = false) MarkdownImportOptionsPayload options,
            HttpServletRequest request) throws IOException {
        requireEdit(request);
        MarkdownImportOptionsPayload resolvedOptions = options == null ? new MarkdownImportOptionsPayload() : options;
        return wikiApplicationService.planMarkdownImport(file.getInputStream(),
                WikiApplicationService.ImportPlanCommand.builder()
                        .targetRootPath(resolvedOptions.getTargetRootPath())
                        .build());
    }

    @PostMapping(value = "/import/markdown/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WikiImportApplyResponse applyMarkdownImport(
            @PathVariable String slug,
            @RequestPart("file") MultipartFile file,
            @RequestPart(name = "options", required = false) MarkdownImportOptionsPayload options,
            HttpServletRequest request) throws IOException {
        AuthContext context = requireEdit(request);
        MarkdownImportOptionsPayload resolvedOptions = options == null ? new MarkdownImportOptionsPayload() : options;
        return wikiApplicationService.applyMarkdownImport(file.getInputStream(),
                WikiApplicationService.ImportApplyCommand.builder()
                        .targetRootPath(resolvedOptions.getTargetRootPath())
                        .items(resolvedOptions.getItems().stream()
                                .map(item -> WikiApplicationService.ImportSelectionCommand.builder()
                                        .sourcePath(item.getSourcePath())
                                        .selected(item.isSelected())
                                        .policy(item.getPolicy())
                                        .build())
                                .toList())
                        .actor(resolveActor(context))
                        .build());
    }

    @GetMapping("/links")
    public WikiLinkStatus getLinkStatus(@PathVariable String slug, @RequestParam(name = "path") String path,
            HttpServletRequest request) {
        requireView(request);
        return wikiApplicationService.getLinkStatus(path);
    }

    @GetMapping("/pages/assets")
    public List<WikiAsset> listAssets(@PathVariable String slug, @RequestParam(name = "path") String path,
            HttpServletRequest request) {
        requireEdit(request);
        return wikiApplicationService.listAssets(path);
    }

    @PostMapping(value = "/pages/assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WikiAsset uploadAsset(@PathVariable String slug, @RequestParam(name = "path") String path,
            @RequestPart("file") MultipartFile file, HttpServletRequest request) throws IOException {
        requireEdit(request);
        return wikiApplicationService.uploadAsset(path, file.getOriginalFilename(), file.getContentType(),
                file.getInputStream());
    }

    @PutMapping("/pages/assets/rename")
    public WikiAsset renameAsset(@PathVariable String slug, @RequestParam(name = "path") String path,
            @Valid @RequestBody RenameAssetPayload payload, HttpServletRequest request) {
        requireEdit(request);
        return wikiApplicationService.renameAsset(path, payload.getOldName(), payload.getNewName());
    }

    @DeleteMapping("/pages/assets")
    public void deleteAsset(@PathVariable String slug, @RequestParam(name = "path") String path,
            @RequestParam(name = "name") String name, HttpServletRequest request) {
        requireEdit(request);
        wikiApplicationService.deleteAsset(path, name);
    }

    @GetMapping("/assets")
    public ResponseEntity<InputStreamResource> getAsset(@PathVariable String slug,
            @RequestParam(name = "path") String path, @RequestParam(name = "name") String name,
            HttpServletRequest request) {
        requireView(request);
        WikiAssetContent assetContent = wikiApplicationService.openAsset(path, name);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + assetContent.getName() + "\"")
                .contentLength(assetContent.getSize())
                .contentType(MediaType.parseMediaType(assetContent.getContentType()))
                .body(new InputStreamResource(assetContent.getInputStream()));
    }

    private AuthContext requireEdit(HttpServletRequest request) {
        String spaceId = (String) request.getAttribute(SpaceResolverFilter.SPACE_ID_ATTRIBUTE);
        return authContextResolver.requireSpaceAccess(request, spaceId, UserRole.EDITOR);
    }

    private AuthContext requireView(HttpServletRequest request) {
        String spaceId = (String) request.getAttribute(SpaceResolverFilter.SPACE_ID_ATTRIBUTE);
        return authContextResolver.requireSpaceAccess(request, spaceId, UserRole.VIEWER);
    }

    private String resolveActor(AuthContext context) {
        if (context.getUser() == null || context.getUser().getUsername() == null
                || context.getUser().getUsername().isBlank()) {
            return "Local editor";
        }
        return context.getUser().getUsername();
    }
}
