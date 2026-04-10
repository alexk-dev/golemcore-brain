package dev.golemcore.brain.application.service;

import dev.golemcore.brain.application.exception.WikiNotFoundException;
import dev.golemcore.brain.application.port.out.WikiRepository;
import dev.golemcore.brain.config.WikiProperties;
import dev.golemcore.brain.domain.WikiAsset;
import dev.golemcore.brain.domain.WikiAssetContent;
import dev.golemcore.brain.domain.WikiConfigResponse;
import dev.golemcore.brain.domain.WikiImportAction;
import dev.golemcore.brain.domain.WikiImportApplyResponse;
import dev.golemcore.brain.domain.WikiImportItem;
import dev.golemcore.brain.domain.WikiImportPlanResponse;
import dev.golemcore.brain.domain.WikiLinkStatus;
import dev.golemcore.brain.domain.WikiLinkStatusItem;
import dev.golemcore.brain.domain.WikiNodeKind;
import dev.golemcore.brain.domain.WikiNodeReference;
import dev.golemcore.brain.domain.WikiPage;
import dev.golemcore.brain.domain.WikiPageDocument;
import dev.golemcore.brain.domain.WikiPageHistoryEntry;
import dev.golemcore.brain.domain.WikiPathLookupResult;
import dev.golemcore.brain.domain.WikiPathLookupSegment;
import dev.golemcore.brain.domain.WikiSearchDocument;
import dev.golemcore.brain.domain.WikiSearchHit;
import dev.golemcore.brain.domain.WikiSearchStatus;
import dev.golemcore.brain.domain.WikiTreeNode;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WikiApplicationService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final WikiRepository wikiRepository;
    private final WikiProperties wikiProperties;

    @PostConstruct
    public void initialize() {
        wikiRepository.initialize();
    }

    public WikiConfigResponse getConfig() {
        return WikiConfigResponse.builder()
                .siteTitle(wikiProperties.getSiteTitle())
                .rootPath("")
                .publicAccess(true)
                .authDisabled(true)
                .hideLinkMetadataSection(false)
                .maxAssetUploadSizeBytes(25L * 1024L * 1024L)
                .build();
    }

    public WikiTreeNode getTree() {
        return toTreeNode(wikiRepository.getRootReference());
    }

    public WikiPage getPage(String path) {
        WikiNodeReference nodeReference = wikiRepository.findReference(path)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(path)));
        return toPage(nodeReference);
    }

    public List<WikiPageHistoryEntry> getPageHistory(String path) {
        return wikiRepository.listPageHistory(path);
    }

    public WikiPage restorePageHistory(String path, String versionId) {
        return toPageDocument(wikiRepository.restorePageVersion(path, versionId));
    }

    public WikiPage createPage(CreatePageCommand command) {
        WikiPageDocument document = wikiRepository.createPage(
                command.getParentPath(),
                command.getTitle(),
                command.getSlug(),
                command.getContent(),
                command.getKind());
        return getPage(document.getPath());
    }

    public WikiPage ensurePage(String path, String targetTitle) {
        Optional<WikiNodeReference> existingReference = wikiRepository.findReference(path);
        if (existingReference.isPresent()) {
            return getPage(existingReference.get().getPath());
        }
        String normalizedPath = normalizePath(path);
        String parentPath = normalizedPath.contains("/") ? normalizedPath.substring(0, normalizedPath.lastIndexOf('/')) : "";
        String slug = normalizedPath.contains("/") ? normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1) : normalizedPath;
        String title = Optional.ofNullable(targetTitle).filter(value -> !value.isBlank()).orElse(humanizePath(slug));
        return createPage(CreatePageCommand.builder()
                .parentPath(parentPath)
                .title(title)
                .slug(slug)
                .content("")
                .kind(WikiNodeKind.PAGE)
                .build());
    }

    public WikiPage updatePage(UpdatePageCommand command) {
        WikiPageDocument document = wikiRepository.updatePage(
                command.getPath(),
                command.getTitle(),
                command.getSlug(),
                command.getContent());
        return getPage(document.getPath());
    }

    public void deletePage(String path) {
        wikiRepository.deletePage(path);
    }

    public WikiPage movePage(MovePageCommand command) {
        WikiPageDocument document = wikiRepository.movePage(
                command.getPath(),
                command.getTargetParentPath(),
                command.getTargetSlug(),
                command.getBeforeSlug());
        return getPage(document.getPath());
    }

    public WikiPage copyPage(CopyPageCommand command) {
        WikiPageDocument document = wikiRepository.copyPage(
                command.getPath(),
                command.getTargetParentPath(),
                command.getTargetSlug(),
                command.getBeforeSlug());
        return getPage(document.getPath());
    }

    public void sortChildren(SortChildrenCommand command) {
        wikiRepository.sortChildren(command.getPath(), command.getOrderedSlugs());
    }

    public List<WikiSearchHit> search(String query) {
        String normalizedQuery = Optional.ofNullable(query).orElse("").trim().toLowerCase(Locale.ROOT);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }
        return wikiRepository.flatten().stream()
                .map(this::toSearchDocument)
                .filter(document -> document.matches(normalizedQuery))
                .sorted((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(left.getTitle(), right.getTitle()))
                .limit(50)
                .map(document -> WikiSearchHit.builder()
                        .id(document.getId())
                        .path(document.getPath())
                        .title(document.getTitle())
                        .excerpt(document.buildExcerpt(normalizedQuery))
                        .parentPath(document.getParentPath())
                        .kind(document.getKind())
                        .build())
                .toList();
    }

    public WikiSearchStatus getSearchStatus() {
        List<WikiNodeReference> references = wikiRepository.flatten();
        Instant lastUpdatedAt = references.stream()
                .map(wikiRepository::readDocument)
                .map(WikiPageDocument::getUpdatedAt)
                .max(Instant::compareTo)
                .orElse(Instant.now());
        return WikiSearchStatus.builder()
                .mode("live-scan")
                .ready(true)
                .indexedDocuments(references.size())
                .lastUpdatedAt(DATE_TIME_FORMATTER.format(lastUpdatedAt))
                .build();
    }

    public WikiImportPlanResponse planMarkdownImport(InputStream inputStream) {
        List<ImportEntry> entries = readImportEntries(inputStream);
        return WikiImportPlanResponse.builder()
                .items(entries.stream().map(this::toImportItem).toList())
                .build();
    }

    public WikiImportApplyResponse applyMarkdownImport(InputStream inputStream) {
        List<ImportEntry> entries = readImportEntries(inputStream);
        List<WikiImportItem> items = new ArrayList<>();
        int createdCount = 0;
        int updatedCount = 0;
        for (ImportEntry entry : entries) {
            WikiImportItem item = toImportItem(entry);
            items.add(item);
            if (item.getAction() == WikiImportAction.CREATE) {
                createPage(CreatePageCommand.builder()
                        .parentPath(entry.getParentPath())
                        .title(entry.getTitle())
                        .slug(entry.getSlug())
                        .content(entry.getBody())
                        .kind(entry.getKind())
                        .build());
                createdCount++;
            } else {
                updatePage(UpdatePageCommand.builder()
                        .path(entry.getPath())
                        .title(entry.getTitle())
                        .slug(entry.getSlug())
                        .content(entry.getBody())
                        .build());
                updatedCount++;
            }
        }
        return WikiImportApplyResponse.builder()
                .importedCount(items.size())
                .createdCount(createdCount)
                .updatedCount(updatedCount)
                .skippedCount(0)
                .items(items)
                .build();
    }

    public WikiLinkStatus getLinkStatus(String path) {
        WikiNodeReference currentReference = wikiRepository.findReference(path)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(path)));
        WikiPageDocument currentDocument = wikiRepository.readDocument(currentReference);
        Map<String, WikiNodeReference> referencesByPath = new LinkedHashMap<>();
        for (WikiNodeReference reference : wikiRepository.flatten()) {
            referencesByPath.put(reference.getPath(), reference);
        }

        List<ResolvedLink> currentOutgoingLinks = extractResolvedLinks(currentReference.getPath(), currentDocument.getBody());
        List<WikiLinkStatusItem> outgoings = new ArrayList<>();
        List<WikiLinkStatusItem> brokenOutgoings = new ArrayList<>();
        for (ResolvedLink link : currentOutgoingLinks) {
            WikiNodeReference targetReference = referencesByPath.get(link.getTargetPath());
            WikiLinkStatusItem item = WikiLinkStatusItem.builder()
                    .fromPageId(currentReference.getId())
                    .fromPath(currentReference.getPath())
                    .fromTitle(currentDocument.getTitle())
                    .toPageId(targetReference == null ? null : targetReference.getId())
                    .toPath(link.getTargetPath())
                    .toTitle(targetReference == null ? humanizePath(link.getTargetPath()) : wikiRepository.readDocument(targetReference).getTitle())
                    .broken(targetReference == null)
                    .build();
            if (targetReference == null) {
                brokenOutgoings.add(item);
            } else {
                outgoings.add(item);
            }
        }

        List<WikiLinkStatusItem> backlinks = new ArrayList<>();
        List<WikiLinkStatusItem> brokenIncoming = new ArrayList<>();
        for (WikiNodeReference candidateReference : wikiRepository.flatten()) {
            if (candidateReference.getPath().equals(currentReference.getPath())) {
                continue;
            }
            WikiPageDocument candidateDocument = wikiRepository.readDocument(candidateReference);
            List<ResolvedLink> candidateLinks = extractResolvedLinks(candidateReference.getPath(), candidateDocument.getBody());
            for (ResolvedLink link : candidateLinks) {
                if (link.getTargetPath().equals(currentReference.getPath())) {
                    backlinks.add(WikiLinkStatusItem.builder()
                            .fromPageId(candidateReference.getId())
                            .fromPath(candidateReference.getPath())
                            .fromTitle(candidateDocument.getTitle())
                            .toPageId(currentReference.getId())
                            .toPath(currentReference.getPath())
                            .toTitle(currentDocument.getTitle())
                            .broken(false)
                            .build());
                }
                if (!referencesByPath.containsKey(link.getTargetPath())) {
                    brokenIncoming.add(WikiLinkStatusItem.builder()
                            .fromPageId(candidateReference.getId())
                            .fromPath(candidateReference.getPath())
                            .fromTitle(candidateDocument.getTitle())
                            .toPageId(null)
                            .toPath(link.getTargetPath())
                            .toTitle(humanizePath(link.getTargetPath()))
                            .broken(true)
                            .build());
                }
            }
        }

        return WikiLinkStatus.builder()
                .backlinks(backlinks)
                .brokenIncoming(brokenIncoming)
                .outgoings(outgoings)
                .brokenOutgoings(brokenOutgoings)
                .build();
    }

    public WikiPathLookupResult lookupPath(String path) {
        String normalizedPath = normalizePath(path);
        String[] segments = normalizedPath.isBlank() ? new String[0] : normalizedPath.split("/");
        List<WikiPathLookupSegment> items = new ArrayList<>();
        String currentPath = "";
        for (String segment : segments) {
            currentPath = currentPath.isBlank() ? segment : currentPath + "/" + segment;
            boolean exists = wikiRepository.findReference(currentPath).isPresent();
            items.add(WikiPathLookupSegment.builder()
                    .slug(segment)
                    .path(currentPath)
                    .exists(exists)
                    .build());
        }
        return WikiPathLookupResult.builder()
                .path(normalizedPath)
                .exists(wikiRepository.findReference(normalizedPath).isPresent())
                .segments(items)
                .build();
    }

    public List<WikiAsset> listAssets(String path) {
        return wikiRepository.listAssets(path);
    }

    public WikiAsset uploadAsset(String path, String fileName, String contentType, InputStream inputStream) {
        return wikiRepository.saveAsset(path, fileName, contentType, inputStream);
    }

    public WikiAsset renameAsset(String path, String oldName, String newName) {
        return wikiRepository.renameAsset(path, oldName, newName);
    }

    public void deleteAsset(String path, String assetName) {
        wikiRepository.deleteAsset(path, assetName);
    }

    public WikiAssetContent openAsset(String path, String assetName) {
        return wikiRepository.openAsset(path, assetName);
    }

    private List<ImportEntry> readImportEntries(InputStream inputStream) {
        try {
            Map<String, ImportEntry> entryByPath = new LinkedHashMap<>();
            Set<String> sectionPaths = new LinkedHashSet<>();
            ZipInputStream zipInputStream = new ZipInputStream(inputStream, StandardCharsets.UTF_8);
            ZipEntry zipEntry;
            while ((zipEntry = zipInputStream.getNextEntry()) != null) {
                if (zipEntry.isDirectory() || !zipEntry.getName().endsWith(".md")) {
                    continue;
                }
                String normalizedEntryPath = normalizeArchiveEntryPath(zipEntry.getName());
                if (normalizedEntryPath.isBlank()) {
                    continue;
                }
                String markdown = new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                ImportEntry entry = toImportEntry(normalizedEntryPath, markdown);
                entryByPath.put(entry.getPath(), entry);
                if (entry.getKind() == WikiNodeKind.SECTION) {
                    sectionPaths.add(entry.getPath());
                }
                String parentPath = entry.getParentPath();
                while (parentPath != null && !parentPath.isBlank()) {
                    sectionPaths.add(parentPath);
                    parentPath = parentPath.contains("/") ? parentPath.substring(0, parentPath.lastIndexOf('/')) : "";
                }
            }

            for (String sectionPath : sectionPaths) {
                if (entryByPath.containsKey(sectionPath)) {
                    continue;
                }
                String slug = sectionPath.substring(sectionPath.lastIndexOf('/') + 1);
                entryByPath.put(sectionPath, ImportEntry.builder()
                        .path(sectionPath)
                        .parentPath(sectionPath.contains("/") ? sectionPath.substring(0, sectionPath.lastIndexOf('/')) : "")
                        .slug(slug)
                        .title(humanizePath(slug))
                        .body("")
                        .kind(WikiNodeKind.SECTION)
                        .implicitSection(true)
                        .sourcePath(sectionPath + "/index.md")
                        .build());
            }

            return entryByPath.values().stream()
                    .sorted((left, right) -> {
                        int depthCompare = Integer.compare(left.getPath().split("/").length, right.getPath().split("/").length);
                        if (depthCompare != 0) {
                            return depthCompare;
                        }
                        return left.getPath().compareTo(right.getPath());
                    })
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read markdown import archive", exception);
        }
    }

    private ImportEntry toImportEntry(String archivePath, String markdown) {
        boolean isSection = archivePath.endsWith("/index.md");
        String normalizedPath = isSection
                ? archivePath.substring(0, archivePath.length() - "/index.md".length())
                : archivePath.substring(0, archivePath.length() - ".md".length());
        String parentPath = normalizedPath.contains("/") ? normalizedPath.substring(0, normalizedPath.lastIndexOf('/')) : "";
        String slug = normalizedPath.contains("/") ? normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1) : normalizedPath;
        String title = extractTitle(markdown, humanizePath(slug));
        String body = extractBody(markdown);
        return ImportEntry.builder()
                .path(normalizedPath)
                .parentPath(parentPath)
                .slug(slug)
                .title(title)
                .body(body)
                .kind(isSection ? WikiNodeKind.SECTION : WikiNodeKind.PAGE)
                .implicitSection(false)
                .sourcePath(archivePath)
                .build();
    }

    private WikiImportItem toImportItem(ImportEntry entry) {
        WikiImportAction action = wikiRepository.findReference(entry.getPath()).isPresent()
                ? WikiImportAction.UPDATE
                : WikiImportAction.CREATE;
        return WikiImportItem.builder()
                .path(entry.getPath())
                .title(entry.getTitle())
                .kind(entry.getKind())
                .action(action)
                .implicitSection(entry.isImplicitSection())
                .sourcePath(entry.getSourcePath())
                .build();
    }

    private String normalizeArchiveEntryPath(String archivePath) {
        String normalized = archivePath.replace('\\', '/').trim();
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith("/") || normalized.contains("..")) {
            throw new IllegalArgumentException("Invalid archive entry path");
        }
        return normalized;
    }

    private String extractTitle(String markdown, String fallbackTitle) {
        String[] lines = markdown.split("\\R", -1);
        if (lines.length > 0 && lines[0].startsWith("# ")) {
            String title = lines[0].substring(2).trim();
            if (!title.isBlank()) {
                return title;
            }
        }
        return fallbackTitle;
    }

    private String extractBody(String markdown) {
        String[] lines = markdown.split("\\R", -1);
        if (lines.length > 0 && lines[0].startsWith("# ")) {
            return Arrays.stream(lines).skip(2).collect(Collectors.joining("\n")).strip();
        }
        return markdown.strip();
    }

    private WikiTreeNode toTreeNode(WikiNodeReference nodeReference) {
        WikiPageDocument document = wikiRepository.readDocument(nodeReference);
        List<WikiTreeNode> children = nodeReference.getKind() == WikiNodeKind.PAGE
                ? List.of()
                : wikiRepository.listChildren(nodeReference).stream().map(this::toTreeNode).toList();
        return WikiTreeNode.builder()
                .id(nodeReference.getId())
                .path(nodeReference.getPath())
                .parentPath(nodeReference.getParentPath())
                .title(document.getTitle())
                .slug(nodeReference.getSlug())
                .kind(nodeReference.getKind())
                .hasChildren(!children.isEmpty())
                .children(children)
                .build();
    }

    private WikiPage toPage(WikiNodeReference nodeReference) {
        WikiPageDocument document = wikiRepository.readDocument(nodeReference);
        return toPageDocument(document);
    }

    private WikiPage toPageDocument(WikiPageDocument document) {
        WikiNodeReference nodeReference = wikiRepository.findReference(document.getPath())
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + document.getPath()));
        List<WikiTreeNode> children = nodeReference.getKind() == WikiNodeKind.PAGE
                ? List.of()
                : wikiRepository.listChildren(nodeReference).stream().map(this::toTreeNode).toList();
        return WikiPage.builder()
                .id(document.getId())
                .path(document.getPath())
                .parentPath(document.getParentPath())
                .title(document.getTitle())
                .slug(document.getSlug())
                .kind(document.getKind())
                .content(document.getBody())
                .createdAt(DATE_TIME_FORMATTER.format(document.getCreatedAt()))
                .updatedAt(DATE_TIME_FORMATTER.format(document.getUpdatedAt()))
                .children(children)
                .build();
    }

    private WikiSearchDocument toSearchDocument(WikiNodeReference nodeReference) {
        WikiPageDocument document = wikiRepository.readDocument(nodeReference);
        return WikiSearchDocument.builder()
                .id(nodeReference.getId())
                .path(nodeReference.getPath())
                .parentPath(document.getParentPath())
                .title(document.getTitle())
                .body(document.getBody())
                .kind(document.getKind())
                .build();
    }

    private List<ResolvedLink> extractResolvedLinks(String currentPath, String markdown) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\[[^\\]]+\\]\\(([^)]+)\\)").matcher(markdown == null ? "" : markdown);
        List<ResolvedLink> links = new ArrayList<>();
        while (matcher.find()) {
            String href = matcher.group(1).trim();
            if (href.isBlank() || href.startsWith("http") || href.startsWith("mailto:") || href.startsWith("#") || href.startsWith("assets/") || href.startsWith("/assets/")) {
                continue;
            }
            links.add(ResolvedLink.builder().targetPath(resolveWikiLinkPath(currentPath, href)).build());
        }
        return links;
    }

    private String resolveWikiLinkPath(String currentPath, String href) {
        String normalizedCurrentPath = normalizePath(currentPath);
        String normalizedHref = href.trim();
        if (normalizedHref.startsWith("/")) {
            return normalizePath(normalizedHref);
        }
        String baseDirectory = normalizedCurrentPath.contains("/")
                ? normalizedCurrentPath.substring(0, normalizedCurrentPath.lastIndexOf('/'))
                : "";
        Path resolved = Paths.get("/" + baseDirectory).resolve(normalizedHref).normalize();
        return normalizePath(resolved.toString());
    }

    private String normalizePath(String path) {
        String normalized = Optional.ofNullable(path).orElse("")
                .replace('\\', '/')
                .trim()
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");
        if (normalized.equals(".")) {
            return "";
        }
        if (normalized.contains("..")) {
            throw new IllegalArgumentException("Path traversal is not allowed");
        }
        return normalized;
    }

    private String humanizePath(String path) {
        String normalized = normalizePath(path);
        if (normalized.isBlank()) {
            return wikiProperties.getSiteTitle();
        }
        String slug = normalized.contains("/") ? normalized.substring(normalized.lastIndexOf('/') + 1) : normalized;
        return Arrays.stream(slug.split("-"))
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    @Value
    @Builder
    private static class ResolvedLink {
        String targetPath;
    }

    @Value
    @Builder
    private static class ImportEntry {
        String path;
        String parentPath;
        String slug;
        String title;
        String body;
        WikiNodeKind kind;
        boolean implicitSection;
        String sourcePath;
    }

    @Value
    @Builder
    public static class CreatePageCommand {
        String parentPath;
        String title;
        String slug;
        String content;
        WikiNodeKind kind;
    }

    @Value
    @Builder
    public static class UpdatePageCommand {
        String path;
        String title;
        String slug;
        String content;
    }

    @Value
    @Builder
    public static class MovePageCommand {
        String path;
        String targetParentPath;
        String targetSlug;
        String beforeSlug;
    }

    @Value
    @Builder
    public static class CopyPageCommand {
        String path;
        String targetParentPath;
        String targetSlug;
        String beforeSlug;
    }

    @Value
    @Builder
    public static class SortChildrenCommand {
        String path;
        List<String> orderedSlugs;
    }
}
