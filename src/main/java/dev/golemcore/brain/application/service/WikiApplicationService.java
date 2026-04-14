package dev.golemcore.brain.application.service;

import dev.golemcore.brain.application.exception.WikiNotFoundException;
import dev.golemcore.brain.application.port.out.BrainSettingsPort;
import dev.golemcore.brain.application.port.out.WikiRepository;
import dev.golemcore.brain.application.service.index.WikiIndexingService;
import dev.golemcore.brain.application.space.SpaceContextHolder;
import dev.golemcore.brain.domain.WikiAsset;
import dev.golemcore.brain.domain.WikiAssetContent;
import dev.golemcore.brain.domain.WikiConfigResponse;
import dev.golemcore.brain.domain.WikiImportAction;
import dev.golemcore.brain.domain.WikiImportApplyResponse;
import dev.golemcore.brain.domain.WikiImportItem;
import dev.golemcore.brain.domain.WikiIndexStatus;
import dev.golemcore.brain.domain.WikiIndexedDocument;
import dev.golemcore.brain.domain.WikiImportPolicy;
import dev.golemcore.brain.domain.WikiImportPlanResponse;
import dev.golemcore.brain.domain.WikiLinkStatus;
import dev.golemcore.brain.domain.WikiLinkStatusItem;
import dev.golemcore.brain.domain.WikiNodeKind;
import dev.golemcore.brain.domain.WikiNodeReference;
import dev.golemcore.brain.domain.WikiPage;
import dev.golemcore.brain.domain.WikiPageDocument;
import dev.golemcore.brain.domain.WikiPageHistoryEntry;
import dev.golemcore.brain.domain.WikiPageHistoryVersion;
import dev.golemcore.brain.domain.WikiPathLookupResult;
import dev.golemcore.brain.domain.WikiPathLookupSegment;
import dev.golemcore.brain.domain.WikiSearchHit;
import dev.golemcore.brain.domain.WikiSemanticSearchResult;
import dev.golemcore.brain.domain.WikiSearchStatus;
import dev.golemcore.brain.domain.WikiTreeNode;
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

@RequiredArgsConstructor
public class WikiApplicationService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final WikiRepository wikiRepository;
    private final BrainSettingsPort brainSettingsPort;
    private final WikiIndexingService wikiIndexingService;

    public void initialize() {
        wikiRepository.initialize();
    }

    public WikiConfigResponse getConfig() {
        return WikiConfigResponse.builder()
                .siteTitle(brainSettingsPort.getSiteTitle())
                .rootPath("")
                .imageVersion(brainSettingsPort.getImageVersion())
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

    public WikiPageHistoryVersion getPageHistoryVersion(String path, String versionId) {
        return wikiRepository.readPageHistoryVersion(path, versionId);
    }

    public WikiPage restorePageHistory(String path, String versionId, String actor) {
        WikiPageHistoryVersion version = wikiRepository.readPageHistoryVersion(path, versionId);
        WikiPageDocument document = wikiRepository.restorePageVersion(
                path,
                versionId,
                actor,
                "Version restored",
                "Rolled back to \"" + version.getTitle() + "\".");
        recordUpsert(document);
        return toPageDocument(document);
    }

    public WikiPage createPage(CreatePageCommand command) {
        WikiPageDocument document = wikiRepository.createPage(
                command.getParentPath(),
                command.getTitle(),
                command.getSlug(),
                command.getContent(),
                command.getKind());
        recordUpsert(document);
        return getPage(document.getPath());
    }

    public WikiPage ensurePage(String path, String targetTitle) {
        Optional<WikiNodeReference> existingReference = wikiRepository.findReference(path);
        if (existingReference.isPresent()) {
            return getPage(existingReference.get().getPath());
        }
        String normalizedPath = normalizePath(path);
        String parentPath = normalizedPath.contains("/") ? normalizedPath.substring(0, normalizedPath.lastIndexOf('/'))
                : "";
        String slug = normalizedPath.contains("/") ? normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1)
                : normalizedPath;
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
        WikiNodeReference nodeReference = wikiRepository.findReference(command.getPath())
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(command.getPath())));
        WikiPageDocument currentDocument = wikiRepository.readDocument(nodeReference);
        List<String> previousPaths = pathsForSubtree(command.getPath());
        WikiPageDocument document = wikiRepository.updatePage(
                command.getPath(),
                command.getTitle(),
                command.getSlug(),
                command.getContent(),
                command.getExpectedRevision(),
                command.getActor(),
                Optional.ofNullable(command.getHistoryReason()).orElse("Manual save"),
                Optional.ofNullable(command.getHistorySummary())
                        .orElse(summarizeManualSave(currentDocument, nodeReference, command)));
        if (normalizePath(command.getPath()).equals(document.getPath())) {
            recordUpsert(document);
        } else {
            recordDeletes(previousPaths);
            recordUpserts(documentsForSubtree(document.getPath()));
        }
        return getPage(document.getPath());
    }

    public void deletePage(String path) {
        List<String> deletedPaths = pathsForSubtree(path);
        wikiRepository.deletePage(path);
        recordDeletes(deletedPaths);
    }

    public WikiPage movePage(MovePageCommand command) {
        List<String> previousPaths = pathsForSubtree(command.getPath());
        WikiPageDocument document = wikiRepository.movePage(
                command.getPath(),
                command.getTargetParentPath(),
                command.getTargetSlug(),
                command.getBeforeSlug());
        recordDeletes(previousPaths);
        recordUpserts(documentsForSubtree(document.getPath()));
        return getPage(document.getPath());
    }

    public WikiPage copyPage(CopyPageCommand command) {
        WikiPageDocument document = wikiRepository.copyPage(
                command.getPath(),
                command.getTargetParentPath(),
                command.getTargetSlug(),
                command.getBeforeSlug());
        recordUpserts(documentsForSubtree(document.getPath()));
        return getPage(document.getPath());
    }

    public WikiPage convertPage(ConvertPageCommand command) {
        WikiPageDocument document = wikiRepository.convertPage(command.getPath(), command.getTargetKind());
        recordUpsert(document);
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
        return wikiIndexingService.search(requireSpaceId(), normalizedQuery);
    }

    public WikiSearchStatus getSearchStatus() {
        WikiIndexStatus status = wikiIndexingService.getStatus(requireSpaceId());
        Instant lastUpdatedAt = Optional.ofNullable(status.getLastUpdatedAt()).orElse(Instant.now());
        return WikiSearchStatus.builder()
                .mode(status.getMode())
                .ready(status.isReady())
                .indexedDocuments(status.getFullTextIndexedDocuments())
                .fullTextIndexedDocuments(status.getFullTextIndexedDocuments())
                .embeddingDocuments(status.getEmbeddingIndexedDocuments())
                .embeddingIndexedDocuments(status.getEmbeddingIndexedDocuments())
                .staleDocuments(status.getStaleDocuments())
                .embeddingsReady(status.isEmbeddingsReady())
                .lastIndexingError(status.getLastIndexingError())
                .embeddingModelId(status.getEmbeddingModelId())
                .lastFullRebuildAt(status.getLastFullRebuildAt() == null
                        ? null
                        : DATE_TIME_FORMATTER.format(status.getLastFullRebuildAt()))
                .lastUpdatedAt(DATE_TIME_FORMATTER.format(lastUpdatedAt))
                .build();
    }

    public WikiSemanticSearchResult semanticSearch(String query) {
        return wikiIndexingService.semanticSearch(requireSpaceId(), query);
    }

    public WikiImportPlanResponse planMarkdownImport(InputStream inputStream) {
        return planMarkdownImport(inputStream, ImportPlanCommand.builder().build());
    }

    public WikiImportPlanResponse planMarkdownImport(InputStream inputStream, ImportPlanCommand command) {
        validateImportTargetRoot(command.getTargetRootPath());
        List<ImportEntry> entries = readImportEntries(inputStream, command.getTargetRootPath());
        List<String> warnings = new ArrayList<>();
        List<WikiImportItem> items = entries.stream()
                .map(entry -> toImportItem(entry, null, true, warnings))
                .toList();
        return WikiImportPlanResponse.builder()
                .targetRootPath(normalizePath(Optional.ofNullable(command.getTargetRootPath()).orElse("")))
                .createCount((int) items.stream().filter(item -> item.getAction() == WikiImportAction.CREATE).count())
                .updateCount((int) items.stream().filter(item -> item.getAction() == WikiImportAction.UPDATE).count())
                .skipCount((int) items.stream().filter(item -> item.getAction() == WikiImportAction.SKIP).count())
                .warnings(warnings)
                .items(items)
                .build();
    }

    public WikiImportApplyResponse applyMarkdownImport(InputStream inputStream) {
        return applyMarkdownImport(inputStream, ImportApplyCommand.builder().build());
    }

    public WikiImportApplyResponse applyMarkdownImport(InputStream inputStream, ImportApplyCommand command) {
        validateImportTargetRoot(command.getTargetRootPath());
        List<ImportEntry> entries = readImportEntries(inputStream, command.getTargetRootPath());
        ensureSectionHierarchy(normalizePath(Optional.ofNullable(command.getTargetRootPath()).orElse("")));
        Map<String, ImportSelectionCommand> selectionBySourcePath = Optional.ofNullable(command.getItems())
                .orElse(List.of()).stream()
                .filter(selection -> selection.getSourcePath() != null && !selection.getSourcePath().isBlank())
                .collect(Collectors.toMap(ImportSelectionCommand::getSourcePath, selection -> selection,
                        (left, right) -> right, LinkedHashMap::new));
        List<WikiImportItem> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int createdCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        for (ImportEntry entry : entries) {
            ImportSelectionCommand selection = selectionBySourcePath.get(entry.getSourcePath());
            boolean selected = isEntrySelected(entry, selection, entries, selectionBySourcePath);
            WikiImportItem item = toImportItem(entry, selection, selected, warnings);
            if (item.getAction() == WikiImportAction.SKIP) {
                items.add(item);
                skippedCount++;
                continue;
            }
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
                        .actor(Optional.ofNullable(command.getActor()).filter(value -> !value.isBlank())
                                .orElse("Import"))
                        .historyReason("Markdown import")
                        .historySummary("Updated from the imported archive.")
                        .build());
                updatedCount++;
            }
            items.add(item);
        }
        return WikiImportApplyResponse.builder()
                .importedCount(createdCount + updatedCount)
                .createdCount(createdCount)
                .updatedCount(updatedCount)
                .skippedCount(skippedCount)
                .importedRootPath(commonImportedRootPath(items))
                .warnings(warnings)
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

        List<ResolvedLink> currentOutgoingLinks = extractResolvedLinks(currentReference.getPath(),
                currentDocument.getBody());
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
                    .toTitle(targetReference == null ? humanizePath(link.getTargetPath())
                            : wikiRepository.readDocument(targetReference).getTitle())
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
            List<ResolvedLink> candidateLinks = extractResolvedLinks(candidateReference.getPath(),
                    candidateDocument.getBody());
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
        WikiAsset asset = wikiRepository.renameAsset(path, oldName, newName);
        WikiNodeReference nodeReference = wikiRepository.findReference(path)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(path)));
        recordUpsert(wikiRepository.readDocument(nodeReference));
        return asset;
    }

    public void deleteAsset(String path, String assetName) {
        wikiRepository.deleteAsset(path, assetName);
    }

    public WikiAssetContent openAsset(String path, String assetName) {
        return wikiRepository.openAsset(path, assetName);
    }

    private List<ImportEntry> readImportEntries(InputStream inputStream, String targetRootPath) {
        try {
            Map<String, ImportEntry> entryByPath = new LinkedHashMap<>();
            Set<String> sectionPaths = new LinkedHashSet<>();
            String normalizedTargetRootPath = normalizePath(Optional.ofNullable(targetRootPath).orElse(""));
            ZipInputStream zipInputStream = new ZipInputStream(inputStream, StandardCharsets.UTF_8);
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            while (zipEntry != null) {
                if (!zipEntry.isDirectory() && zipEntry.getName().endsWith(".md")) {
                    ImportEntry entry = readImportEntry(zipInputStream, zipEntry, normalizedTargetRootPath);
                    if (entry != null) {
                        entryByPath.put(entry.getPath(), entry);
                        collectSectionPaths(entry, normalizedTargetRootPath, sectionPaths);
                    }
                }
                zipEntry = zipInputStream.getNextEntry();
            }

            for (String sectionPath : sectionPaths) {
                if (entryByPath.containsKey(sectionPath)) {
                    continue;
                }
                String slug = sectionPath.substring(sectionPath.lastIndexOf('/') + 1);
                entryByPath.put(sectionPath, ImportEntry.builder()
                        .path(sectionPath)
                        .parentPath(
                                sectionPath.contains("/") ? sectionPath.substring(0, sectionPath.lastIndexOf('/')) : "")
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
                        int depthCompare = Integer.compare(left.getPath().split("/").length,
                                right.getPath().split("/").length);
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

    private ImportEntry readImportEntry(
            ZipInputStream zipInputStream,
            ZipEntry zipEntry,
            String normalizedTargetRootPath) throws IOException {
        String normalizedEntryPath = normalizeArchiveEntryPath(zipEntry.getName());
        if (normalizedEntryPath.isBlank()) {
            return null;
        }
        String markdown = new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
        return toImportEntry(normalizedEntryPath, markdown, normalizedTargetRootPath);
    }

    private void collectSectionPaths(
            ImportEntry entry,
            String normalizedTargetRootPath,
            Set<String> sectionPaths) {
        if (entry.getKind() == WikiNodeKind.SECTION) {
            sectionPaths.add(entry.getPath());
        }
        String parentPath = entry.getParentPath();
        while (parentPath != null && !parentPath.isBlank()) {
            if (!normalizedTargetRootPath.isBlank() && parentPath.equals(normalizedTargetRootPath)) {
                break;
            }
            sectionPaths.add(parentPath);
            parentPath = parentPath.contains("/") ? parentPath.substring(0, parentPath.lastIndexOf('/')) : "";
        }
    }

    private ImportEntry toImportEntry(String archivePath, String markdown, String targetRootPath) {
        boolean isSection = archivePath.endsWith("/index.md");
        String importedPath = isSection
                ? archivePath.substring(0, archivePath.length() - "/index.md".length())
                : archivePath.substring(0, archivePath.length() - ".md".length());
        String normalizedPath = joinPath(targetRootPath, importedPath);
        String parentPath = normalizedPath.contains("/") ? normalizedPath.substring(0, normalizedPath.lastIndexOf('/'))
                : "";
        String slug = normalizedPath.contains("/") ? normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1)
                : normalizedPath;
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

    private WikiImportItem toImportItem(
            ImportEntry entry,
            ImportSelectionCommand selection,
            boolean selected,
            List<String> warnings) {
        boolean existing = wikiRepository.findReference(entry.getPath()).isPresent();
        WikiImportPolicy policy = resolveImportPolicy(entry, existing, selection);
        WikiImportAction action;
        String note;
        if (!selected) {
            action = WikiImportAction.SKIP;
            note = "Skipped by selection.";
        } else if (existing && policy == WikiImportPolicy.KEEP_EXISTING) {
            action = WikiImportAction.SKIP;
            note = entry.isImplicitSection()
                    ? "Existing parent section will be kept."
                    : "Existing page will be kept.";
            warnings.add("Kept existing content at " + entry.getPath());
        } else {
            action = existing ? WikiImportAction.UPDATE : WikiImportAction.CREATE;
            note = buildImportNote(entry, action, existing, selection);
        }
        return WikiImportItem.builder()
                .path(entry.getPath())
                .title(entry.getTitle())
                .kind(entry.getKind())
                .action(action)
                .policy(policy)
                .implicitSection(entry.isImplicitSection())
                .existing(existing)
                .selected(selected)
                .sourcePath(entry.getSourcePath())
                .note(note)
                .build();
    }

    private WikiImportPolicy resolveImportPolicy(ImportEntry entry, boolean existing,
            ImportSelectionCommand selection) {
        if (!existing) {
            return WikiImportPolicy.OVERWRITE;
        }
        if (selection != null && selection.getPolicy() != null) {
            return selection.getPolicy();
        }
        if (entry.isImplicitSection()) {
            return WikiImportPolicy.KEEP_EXISTING;
        }
        return WikiImportPolicy.OVERWRITE;
    }

    private boolean isEntrySelected(
            ImportEntry entry,
            ImportSelectionCommand selection,
            List<ImportEntry> allEntries,
            Map<String, ImportSelectionCommand> selectionBySourcePath) {
        boolean explicitlySelected = selection == null || selection.isSelected();
        if (entry.getKind() == WikiNodeKind.PAGE) {
            return explicitlySelected;
        }
        boolean hasSelectedDescendant = allEntries.stream()
                .filter(candidate -> !candidate.getSourcePath().equals(entry.getSourcePath()))
                .filter(candidate -> candidate.getPath().startsWith(entry.getPath() + "/"))
                .anyMatch(candidate -> {
                    ImportSelectionCommand candidateSelection = selectionBySourcePath.get(candidate.getSourcePath());
                    return candidateSelection == null || candidateSelection.isSelected();
                });
        return explicitlySelected || hasSelectedDescendant;
    }

    private String buildImportNote(ImportEntry entry, WikiImportAction action, boolean existing,
            ImportSelectionCommand selection) {
        if (entry.getKind().isContainer() && selection != null && !selection.isSelected()) {
            return existing
                    ? "Required ancestor section will be kept for selected descendants."
                    : "Required ancestor section will be created for selected descendants.";
        }
        if (entry.isImplicitSection()) {
            return existing ? "Parent section will be updated from the archive."
                    : "Parent section will be created automatically.";
        }
        if (action == WikiImportAction.CREATE) {
            return "New content will be created.";
        }
        return "Existing content will be overwritten.";
    }

    private String commonImportedRootPath(List<WikiImportItem> items) {
        List<String> importedPaths = items.stream()
                .filter(item -> item.getAction() != WikiImportAction.SKIP)
                .map(WikiImportItem::getPath)
                .toList();
        if (importedPaths.isEmpty()) {
            return "";
        }
        String common = importedPaths.getFirst();
        for (String path : importedPaths.stream().skip(1).toList()) {
            common = sharedPathPrefix(common, path);
            if (common.isBlank()) {
                break;
            }
        }
        return common;
    }

    private String sharedPathPrefix(String left, String right) {
        List<String> leftSegments = left.isBlank() ? List.of() : Arrays.asList(left.split("/"));
        List<String> rightSegments = right.isBlank() ? List.of() : Arrays.asList(right.split("/"));
        List<String> sharedSegments = new ArrayList<>();
        for (int index = 0; index < Math.min(leftSegments.size(), rightSegments.size()); index++) {
            if (!leftSegments.get(index).equals(rightSegments.get(index))) {
                break;
            }
            sharedSegments.add(leftSegments.get(index));
        }
        return String.join("/", sharedSegments);
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

    private void validateImportTargetRoot(String targetRootPath) {
        String normalizedTargetRoot = normalizePath(Optional.ofNullable(targetRootPath).orElse(""));
        if (normalizedTargetRoot.isBlank()) {
            return;
        }
        wikiRepository.findReference(normalizedTargetRoot).ifPresent(reference -> {
            if (!reference.getKind().isContainer()) {
                throw new IllegalArgumentException("Import target must be a section or the wiki root");
            }
        });
    }

    private String joinPath(String parentPath, String slug) {
        String normalizedParentPath = normalizePath(parentPath);
        String normalizedSlug = normalizePath(slug);
        if (normalizedParentPath.isBlank()) {
            return normalizedSlug;
        }
        if (normalizedSlug.isBlank()) {
            return normalizedParentPath;
        }
        return normalizedParentPath + "/" + normalizedSlug;
    }

    private void ensureSectionHierarchy(String path) {
        String normalizedPath = normalizePath(path);
        if (normalizedPath.isBlank()) {
            return;
        }
        String[] segments = normalizedPath.split("/");
        String currentPath = "";
        for (String segment : segments) {
            String nextPath = joinPath(currentPath, segment);
            Optional<WikiNodeReference> existingReference = wikiRepository.findReference(nextPath);
            if (existingReference.isPresent()) {
                if (!existingReference.get().getKind().isContainer()) {
                    throw new IllegalArgumentException("Import target must be a section or the wiki root");
                }
                currentPath = nextPath;
                continue;
            }
            createPage(CreatePageCommand.builder()
                    .parentPath(currentPath)
                    .title(humanizePath(segment))
                    .slug(segment)
                    .content("")
                    .kind(WikiNodeKind.SECTION)
                    .build());
            currentPath = nextPath;
        }
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
                .revision(document.getRevision())
                .children(children)
                .build();
    }

    private void recordUpsert(WikiPageDocument document) {
        wikiIndexingService.recordUpsert(requireSpaceId(), toIndexedDocument(document));
    }

    private void recordUpserts(List<WikiIndexedDocument> documents) {
        wikiIndexingService.recordUpserts(requireSpaceId(), documents);
    }

    private void recordDeletes(List<String> paths) {
        wikiIndexingService.recordDeletes(requireSpaceId(), paths);
    }

    private List<String> pathsForSubtree(String path) {
        String normalizedPath = normalizePath(path);
        return wikiRepository.flatten().stream()
                .map(WikiNodeReference::getPath)
                .filter(candidatePath -> candidatePath.equals(normalizedPath)
                        || (!normalizedPath.isBlank() && candidatePath.startsWith(normalizedPath + "/")))
                .toList();
    }

    private List<WikiIndexedDocument> documentsForSubtree(String path) {
        String normalizedPath = normalizePath(path);
        return wikiRepository.listDocuments(requireSpaceId()).stream()
                .filter(document -> document.getPath().equals(normalizedPath)
                        || (!normalizedPath.isBlank() && document.getPath().startsWith(normalizedPath + "/")))
                .toList();
    }

    private WikiIndexedDocument toIndexedDocument(WikiPageDocument document) {
        return WikiIndexedDocument.builder()
                .id(document.getId())
                .path(document.getPath())
                .parentPath(document.getParentPath())
                .title(document.getTitle())
                .body(document.getBody())
                .kind(document.getKind())
                .updatedAt(document.getUpdatedAt())
                .revision(document.getRevision())
                .build();
    }

    private String requireSpaceId() {
        return SpaceContextHolder.require();
    }

    private List<ResolvedLink> extractResolvedLinks(String currentPath, String markdown) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\[[^\\]]+\\]\\(([^)]+)\\)")
                .matcher(markdown == null ? "" : markdown);
        List<ResolvedLink> links = new ArrayList<>();
        while (matcher.find()) {
            String href = matcher.group(1).trim();
            if (href.isBlank() || href.startsWith("http") || href.startsWith("mailto:") || href.startsWith("#")
                    || href.startsWith("assets/") || href.startsWith("/assets/")
                    || href.startsWith("/api/assets") || href.startsWith("/api/spaces/")) {
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
        if (".".equals(normalized)) {
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
            return brainSettingsPort.getSiteTitle();
        }
        String slug = normalized.contains("/") ? normalized.substring(normalized.lastIndexOf('/') + 1) : normalized;
        return Arrays.stream(slug.split("-"))
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1))
                .collect(Collectors.joining(" "));
    }

    private String summarizeManualSave(WikiPageDocument currentDocument, WikiNodeReference nodeReference,
            UpdatePageCommand command) {
        List<String> changes = new ArrayList<>();
        String nextTitle = Optional.ofNullable(command.getTitle()).orElse("").trim();
        if (!nextTitle.equals(currentDocument.getTitle())) {
            changes.add("title");
        }
        String nextContent = Optional.ofNullable(command.getContent()).orElse("").strip();
        if (!nextContent.equals(currentDocument.getBody())) {
            changes.add("content");
        }
        String nextSlug = nodeReference.getKind() == WikiNodeKind.ROOT
                ? nodeReference.getSlug()
                : slugify(Optional.ofNullable(command.getSlug()).filter(value -> !value.isBlank()).orElse(nextTitle));
        if (!nextSlug.equals(currentDocument.getSlug())) {
            changes.add("path");
        }
        if (changes.isEmpty()) {
            return "Saved without visible changes.";
        }
        return "Updated " + String.join(", ", changes) + ".";
    }

    private String slugify(String input) {
        String slug = Optional.ofNullable(input).orElse("")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        if (slug.isBlank()) {
            throw new IllegalArgumentException("Slug cannot be empty");
        }
        return slug;
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
        String expectedRevision;
        String actor;
        String historyReason;
        String historySummary;
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
    public static class ConvertPageCommand {
        String path;
        WikiNodeKind targetKind;
    }

    @Value
    @Builder
    public static class SortChildrenCommand {
        String path;
        List<String> orderedSlugs;
    }

    @Value
    @Builder
    public static class ImportPlanCommand {
        @Builder.Default
        String targetRootPath = "";
    }

    @Value
    @Builder
    public static class ImportApplyCommand {
        @Builder.Default
        String targetRootPath = "";
        @Builder.Default
        List<ImportSelectionCommand> items = List.of();
        String actor;
    }

    @Value
    @Builder
    public static class ImportSelectionCommand {
        String sourcePath;
        @Builder.Default
        boolean selected = true;
        @Builder.Default
        WikiImportPolicy policy = WikiImportPolicy.OVERWRITE;
    }
}
