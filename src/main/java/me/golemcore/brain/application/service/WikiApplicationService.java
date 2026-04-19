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

import me.golemcore.brain.application.exception.WikiNotFoundException;
import me.golemcore.brain.application.port.out.BrainSettingsPort;
import me.golemcore.brain.application.port.out.WikiAccessStatsPort;
import me.golemcore.brain.application.port.out.WikiRepository;
import me.golemcore.brain.application.service.index.WikiIndexingService;
import me.golemcore.brain.application.space.SpaceContextHolder;
import me.golemcore.brain.domain.WikiAccessStats;
import me.golemcore.brain.domain.WikiAsset;
import me.golemcore.brain.domain.WikiAssetContent;
import me.golemcore.brain.domain.WikiConfigResponse;
import me.golemcore.brain.domain.WikiImportAction;
import me.golemcore.brain.domain.WikiImportApplyResponse;
import me.golemcore.brain.domain.WikiImportItem;
import me.golemcore.brain.domain.WikiIndexStatus;
import me.golemcore.brain.domain.WikiIndexedDocument;
import me.golemcore.brain.domain.WikiImportPolicy;
import me.golemcore.brain.domain.WikiImportPlanResponse;
import me.golemcore.brain.domain.WikiGraphOrphan;
import me.golemcore.brain.domain.WikiGraphSummary;
import me.golemcore.brain.domain.WikiLinkStatus;
import me.golemcore.brain.domain.WikiLinkStatusItem;
import me.golemcore.brain.domain.WikiNodeKind;
import me.golemcore.brain.domain.WikiNodeReference;
import me.golemcore.brain.domain.WikiPage;
import me.golemcore.brain.application.exception.WikiEditConflictException;
import me.golemcore.brain.domain.WikiPageDocument;
import me.golemcore.brain.domain.WikiPatchOperation;
import me.golemcore.brain.domain.WikiPageHistoryEntry;
import me.golemcore.brain.domain.WikiTxOperationType;
import me.golemcore.brain.domain.WikiTxResult;
import me.golemcore.brain.domain.WikiTxResult.WikiTxOperationResult;
import me.golemcore.brain.domain.WikiPageHistoryVersion;
import me.golemcore.brain.domain.WikiPathLookupResult;
import me.golemcore.brain.domain.WikiPathLookupSegment;
import me.golemcore.brain.domain.WikiSearchHit;
import me.golemcore.brain.domain.WikiSearchMode;
import me.golemcore.brain.domain.WikiSearchResult;
import me.golemcore.brain.domain.WikiSearchStatus;
import me.golemcore.brain.domain.WikiTreeNode;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * Coordinates wiki use cases without binding the application layer to HTTP,
 * filesystem, or indexing implementation details.
 */
@Slf4j
@RequiredArgsConstructor
public class WikiApplicationService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private final WikiRepository wikiRepository;
    private final BrainSettingsPort brainSettingsPort;
    private final WikiIndexingService wikiIndexingService;
    private final WikiAccessStatsPort wikiAccessStatsPort;
    private final WikiFrontmatterCodec wikiFrontmatterCodec = new WikiFrontmatterCodec();
    private final WikiPatchApplier wikiPatchApplier = new WikiPatchApplier();
    private final Map<String, ReentrantLock> spaceMutationLocks = new ConcurrentHashMap<>();

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
        if (nodeReference.getKind() == WikiNodeKind.PAGE) {
            try {
                wikiAccessStatsPort.recordAccess(requireSpaceId(), nodeReference.getPath());
            } catch (RuntimeException exception) {
                log.warn("Failed to record access stats for {}", nodeReference.getPath(), exception);
            }
        }
        return toPage(nodeReference);
    }

    private WikiPage readPageSilent(String path) {
        WikiNodeReference nodeReference = wikiRepository.findReference(path)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(path)));
        return toPage(nodeReference);
    }

    private <T> T withSpaceLock(Supplier<T> operation) {
        ReentrantLock lock = spaceMutationLocks.computeIfAbsent(requireSpaceId(), id -> new ReentrantLock());
        lock.lock();
        try {
            return operation.get();
        } finally {
            lock.unlock();
        }
    }

    private void withSpaceLockVoid(Runnable operation) {
        withSpaceLock(() -> {
            operation.run();
            return null;
        });
    }

    public List<WikiAccessStats> listTopAccessed(int limit) {
        String spaceId = requireSpaceId();
        // Fetch the full ranked snapshot so orphan filtering can't drop below `limit`.
        // The adapter keeps stats in memory, so the cost is O(entries), not an extra
        // I/O.
        List<WikiAccessStats> raw = wikiAccessStatsPort.listTop(spaceId, 0);
        List<WikiAccessStats> live = new ArrayList<>();
        List<String> orphans = new ArrayList<>();
        for (WikiAccessStats stat : raw) {
            if (wikiRepository.findReference(stat.getPath()).isPresent()) {
                live.add(stat);
            } else {
                orphans.add(stat.getPath());
            }
        }
        if (!orphans.isEmpty()) {
            // Batch-GC out-of-band deletions so ghosts don't accumulate in the stats file.
            wikiAccessStatsPort.removePaths(spaceId, orphans);
        }
        if (limit > 0 && live.size() > limit) {
            return live.subList(0, limit);
        }
        return live;
    }

    public List<WikiPageHistoryEntry> getPageHistory(String path) {
        return wikiRepository.listPageHistory(path);
    }

    public WikiPageHistoryVersion getPageHistoryVersion(String path, String versionId) {
        return wikiRepository.readPageHistoryVersion(path, versionId);
    }

    public WikiPage restorePageHistory(String path, String versionId, String actor) {
        return withSpaceLock(() -> {
            WikiPageHistoryVersion version = wikiRepository.readPageHistoryVersion(path, versionId);
            WikiPageDocument document = wikiRepository.restorePageVersion(
                    path,
                    versionId,
                    actor,
                    "Version restored",
                    "Rolled back to \"" + version.getTitle() + "\".");
            recordUpsert(document);
            return toPageDocument(document);
        });
    }

    public WikiPage createPage(CreatePageCommand command) {
        return withSpaceLock(() -> {
            String bodyWithFrontmatter = wikiFrontmatterCodec.render(command.getTags(), command.getSummary(),
                    Optional.ofNullable(command.getContent()).orElse(""));
            WikiPageDocument document = wikiRepository.createPage(
                    command.getParentPath(),
                    command.getTitle(),
                    command.getSlug(),
                    bodyWithFrontmatter,
                    command.getKind());
            recordUpsert(document);
            return readPageSilent(document.getPath());
        });
    }

    public WikiPage ensurePage(String path, String targetTitle) {
        return withSpaceLock(() -> {
            Optional<WikiNodeReference> existingReference = wikiRepository.findReference(path);
            if (existingReference.isPresent()) {
                return readPageSilent(existingReference.get().getPath());
            }
            String normalizedPath = normalizePath(path);
            String parentPath = normalizedPath.contains("/")
                    ? normalizedPath.substring(0, normalizedPath.lastIndexOf('/'))
                    : "";
            String slug = normalizedPath.contains("/") ? normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1)
                    : normalizedPath;
            String title = Optional.ofNullable(targetTitle).filter(value -> !value.isBlank())
                    .orElse(humanizePath(slug));
            return createPage(CreatePageCommand.builder()
                    .parentPath(parentPath)
                    .title(title)
                    .slug(slug)
                    .content("")
                    .kind(WikiNodeKind.PAGE)
                    .build());
        });
    }

    public WikiPage updatePage(UpdatePageCommand command) {
        return withSpaceLock(() -> {
            WikiNodeReference nodeReference = wikiRepository.findReference(command.getPath())
                    .orElseThrow(
                            () -> new WikiNotFoundException("Page not found: " + normalizePath(command.getPath())));
            WikiPageDocument currentDocument = wikiRepository.readDocument(nodeReference);
            List<String> previousPaths = pathsForSubtree(command.getPath());
            String bodyWithFrontmatter = wikiFrontmatterCodec.render(command.getTags(), command.getSummary(),
                    Optional.ofNullable(command.getContent()).orElse(""));
            WikiPageDocument document = wikiRepository.updatePage(
                    command.getPath(),
                    command.getTitle(),
                    command.getSlug(),
                    bodyWithFrontmatter,
                    command.getExpectedRevision(),
                    command.getActor(),
                    Optional.ofNullable(command.getHistoryReason()).orElse("Manual save"),
                    Optional.ofNullable(command.getHistorySummary())
                            .orElse(summarizeManualSave(currentDocument, nodeReference, command)));
            if (normalizePath(command.getPath()).equals(document.getPath())) {
                recordUpsert(document);
            } else {
                // A rename changes every descendant path, so remove stale index records
                // before writing the current subtree back.
                recordDeletes(previousPaths);
                recordUpserts(documentsForSubtree(document.getPath()));
                wikiAccessStatsPort.removeSubtree(requireSpaceId(), normalizePath(command.getPath()));
            }
            return readPageSilent(document.getPath());
        });
    }

    public void deletePage(String path) {
        withSpaceLockVoid(() -> {
            String normalizedPath = normalizePath(path);
            List<String> deletedPaths = pathsForSubtree(path);
            wikiRepository.deletePage(path);
            recordDeletes(deletedPaths);
            if (!normalizedPath.isBlank()) {
                wikiAccessStatsPort.removeSubtree(requireSpaceId(), normalizedPath);
            }
        });
    }

    public WikiTxResult applyTransaction(TransactionCommand command) {
        return withSpaceLock(() -> applyTransactionLocked(command));
    }

    private WikiTxResult applyTransactionLocked(TransactionCommand command) {
        List<TxOpCommand> ops = Optional.ofNullable(command.getOperations()).orElse(List.of());
        Set<String> reservedCreatePaths = new LinkedHashSet<>();
        for (TxOpCommand op : ops) {
            switch (op.getOp()) {
            case CREATE -> {
                if (op.getTitle() == null || op.getTitle().isBlank()) {
                    throw new IllegalArgumentException("CREATE op requires non-blank title");
                }
                String targetPath = resolveTransactionCreateTargetPath(op);
                if (wikiRepository.findReference(targetPath).isPresent()) {
                    throw new IllegalArgumentException("A page with this slug already exists: " + targetPath);
                }
                if (!reservedCreatePaths.add(targetPath)) {
                    throw new IllegalArgumentException("Duplicate CREATE target in transaction: " + targetPath);
                }
            }
            case UPDATE -> {
                WikiNodeReference reference = wikiRepository.findReference(op.getPath())
                        .orElseThrow(() -> new WikiNotFoundException(
                                "Page not found: " + normalizePath(op.getPath())));
                if (op.getExpectedRevision() != null && !op.getExpectedRevision().isBlank()) {
                    WikiPageDocument current = wikiRepository.readDocument(reference);
                    if (!op.getExpectedRevision().equals(current.getRevision())) {
                        throw new WikiEditConflictException(op.getExpectedRevision(), current);
                    }
                }
            }
            case DELETE -> wikiRepository.findReference(op.getPath())
                    .orElseThrow(() -> new WikiNotFoundException(
                            "Page not found: " + normalizePath(op.getPath())));
            default -> throw new IllegalArgumentException("Unsupported tx op: " + op.getOp());
            }
        }
        List<WikiTxOperationResult> results = new ArrayList<>();
        for (TxOpCommand op : ops) {
            switch (op.getOp()) {
            case CREATE -> {
                WikiPage created = createPage(CreatePageCommand.builder()
                        .parentPath(Optional.ofNullable(op.getParentPath()).orElse(""))
                        .title(op.getTitle())
                        .slug(op.getSlug())
                        .content(Optional.ofNullable(op.getContent()).orElse(""))
                        .kind(Optional.ofNullable(op.getKind()).orElse(WikiNodeKind.PAGE))
                        .build());
                results.add(WikiTxOperationResult.builder()
                        .op(WikiTxOperationType.CREATE)
                        .path(created.getPath())
                        .revision(created.getRevision())
                        .build());
            }
            case UPDATE -> {
                WikiPage updated = updatePage(UpdatePageCommand.builder()
                        .path(op.getPath())
                        .title(op.getTitle())
                        .slug(op.getSlug())
                        .content(Optional.ofNullable(op.getContent()).orElse(""))
                        .expectedRevision(op.getExpectedRevision())
                        .actor(command.getActor())
                        .historyReason("Tx apply")
                        .build());
                results.add(WikiTxOperationResult.builder()
                        .op(WikiTxOperationType.UPDATE)
                        .path(updated.getPath())
                        .revision(updated.getRevision())
                        .build());
            }
            case DELETE -> {
                String path = normalizePath(op.getPath());
                deletePage(path);
                results.add(WikiTxOperationResult.builder()
                        .op(WikiTxOperationType.DELETE)
                        .path(path)
                        .revision(null)
                        .build());
            }
            default -> throw new IllegalArgumentException("Unsupported tx op: " + op.getOp());
            }
        }
        return WikiTxResult.builder().results(results).build();
    }

    private String resolveTransactionCreateTargetPath(TxOpCommand op) {
        String parentPath = normalizePath(Optional.ofNullable(op.getParentPath()).orElse(""));
        WikiNodeReference parentReference = wikiRepository.findReference(parentPath)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + parentPath));
        if (!parentReference.getKind().isContainer()) {
            throw new IllegalArgumentException("Target path is not a section");
        }
        String requestedSlug = Optional.ofNullable(op.getSlug()).orElse("");
        String resolvedSlug = slugify(requestedSlug.isBlank() ? op.getTitle() : requestedSlug);
        return joinPath(parentPath, resolvedSlug);
    }

    public WikiPage patchPage(PatchPageCommand command) {
        return withSpaceLock(() -> {
            WikiNodeReference nodeReference = wikiRepository.findReference(command.getPath())
                    .orElseThrow(
                            () -> new WikiNotFoundException("Page not found: " + normalizePath(command.getPath())));
            WikiPageDocument currentDocument = wikiRepository.readDocument(nodeReference);
            String patchedBody = wikiPatchApplier.apply(currentDocument.getBody(), command);
            String reason = wikiPatchApplier.buildReason(command.getOperation(), command.getHeading());
            String summary = wikiPatchApplier.buildSummary(command, currentDocument.getBody(), patchedBody);
            WikiPageDocument document = wikiRepository.updatePage(
                    command.getPath(),
                    currentDocument.getTitle(),
                    currentDocument.getSlug(),
                    patchedBody,
                    command.getExpectedRevision(),
                    command.getActor(),
                    reason,
                    summary);
            recordUpsert(document);
            return readPageSilent(document.getPath());
        });
    }

    public WikiPage movePage(MovePageCommand command) {
        return withSpaceLock(() -> {
            String originalPath = normalizePath(command.getPath());
            List<String> previousPaths = pathsForSubtree(command.getPath());
            WikiPageDocument document = wikiRepository.movePage(
                    command.getPath(),
                    command.getTargetParentPath(),
                    command.getTargetSlug(),
                    command.getBeforeSlug());
            recordDeletes(previousPaths);
            recordUpserts(documentsForSubtree(document.getPath()));
            if (!originalPath.isBlank() && !originalPath.equals(document.getPath())) {
                wikiAccessStatsPort.removeSubtree(requireSpaceId(), originalPath);
            }
            return readPageSilent(document.getPath());
        });
    }

    public WikiPage copyPage(CopyPageCommand command) {
        return withSpaceLock(() -> {
            WikiPageDocument document = wikiRepository.copyPage(
                    command.getPath(),
                    command.getTargetParentPath(),
                    command.getTargetSlug(),
                    command.getBeforeSlug());
            recordUpserts(documentsForSubtree(document.getPath()));
            return readPageSilent(document.getPath());
        });
    }

    public WikiPage convertPage(ConvertPageCommand command) {
        return withSpaceLock(() -> {
            WikiPageDocument document = wikiRepository.convertPage(command.getPath(), command.getTargetKind());
            recordUpsert(document);
            return readPageSilent(document.getPath());
        });
    }

    public void sortChildren(SortChildrenCommand command) {
        withSpaceLockVoid(() -> wikiRepository.sortChildren(command.getPath(), command.getOrderedSlugs()));
    }

    public List<WikiSearchHit> search(String query) {
        String normalizedQuery = Optional.ofNullable(query).orElse("").trim().toLowerCase(Locale.ROOT);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }
        return wikiIndexingService.search(requireSpaceId(), normalizedQuery);
    }

    public WikiSearchResult search(SearchCommand command) {
        WikiSearchMode mode = WikiSearchMode.from(command == null ? null : command.getMode());
        String query = Optional.ofNullable(command == null ? null : command.getQuery()).orElse("").trim();
        int limit = Optional.ofNullable(command == null ? null : command.getLimit())
                .orElse(WikiSearchMode.FTS.equals(mode) ? 50 : 10);
        return wikiIndexingService.search(requireSpaceId(), query, mode, limit);
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
        // Parse outside the lock so unzip + markdown parsing doesn't block concurrent
        // writes.
        validateImportTargetRoot(command.getTargetRootPath());
        List<ImportEntry> entries = readImportEntries(inputStream, command.getTargetRootPath());
        Map<String, ImportSelectionCommand> selectionBySourcePath = Optional.ofNullable(command.getItems())
                .orElse(List.of()).stream()
                .filter(selection -> selection.getSourcePath() != null && !selection.getSourcePath().isBlank())
                .collect(Collectors.toMap(ImportSelectionCommand::getSourcePath, selection -> selection,
                        (left, right) -> right, LinkedHashMap::new));
        return withSpaceLock(() -> applyMarkdownImportLocked(entries, selectionBySourcePath, command));
    }

    private WikiImportApplyResponse applyMarkdownImportLocked(
            List<ImportEntry> entries,
            Map<String, ImportSelectionCommand> selectionBySourcePath,
            ImportApplyCommand command) {
        ensureSectionHierarchy(normalizePath(Optional.ofNullable(command.getTargetRootPath()).orElse("")));
        List<WikiImportItem> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int createdCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;
        for (ImportEntry entry : entries) {
            ImportSelectionCommand selection = selectionBySourcePath.get(entry.getSourcePath());
            // Section rows may be selected implicitly when at least one selected page
            // needs that section as its parent.
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

    public WikiGraphSummary getGraphSummary() {
        Map<String, WikiNodeReference> referencesByPath = new LinkedHashMap<>();
        for (WikiNodeReference reference : wikiRepository.flatten()) {
            referencesByPath.put(reference.getPath(), reference);
        }
        Set<String> linkedTargets = new LinkedHashSet<>();
        List<WikiLinkStatusItem> dangling = new ArrayList<>();
        for (WikiNodeReference reference : referencesByPath.values()) {
            if (reference.getKind() == WikiNodeKind.ROOT) {
                continue;
            }
            WikiPageDocument document = wikiRepository.readDocument(reference);
            for (ResolvedLink link : extractResolvedLinks(reference.getPath(), document.getBody())) {
                WikiNodeReference target = referencesByPath.get(link.getTargetPath());
                if (target == null) {
                    dangling.add(WikiLinkStatusItem.builder()
                            .fromPageId(reference.getId())
                            .fromPath(reference.getPath())
                            .fromTitle(document.getTitle())
                            .toPageId(null)
                            .toPath(link.getTargetPath())
                            .toTitle(humanizePath(link.getTargetPath()))
                            .broken(true)
                            .build());
                } else {
                    linkedTargets.add(target.getPath());
                }
            }
        }
        List<WikiGraphOrphan> orphans = new ArrayList<>();
        for (WikiNodeReference reference : referencesByPath.values()) {
            if (reference.getKind() == WikiNodeKind.ROOT || reference.getKind() == WikiNodeKind.SECTION) {
                continue;
            }
            if (linkedTargets.contains(reference.getPath())) {
                continue;
            }
            WikiPageDocument document = wikiRepository.readDocument(reference);
            orphans.add(WikiGraphOrphan.builder()
                    .pageId(reference.getId())
                    .path(reference.getPath())
                    .title(document.getTitle())
                    .build());
        }
        return WikiGraphSummary.builder().orphans(orphans).dangling(dangling).build();
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
                        // Markdown archives often omit empty parent index files; keep
                        // those parents materialized in the import plan.
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
        if (lines.length == 0 || !lines[0].startsWith("# ")) {
            return markdown.strip();
        }
        int startIndex = 1;
        if (startIndex < lines.length && lines[startIndex].isBlank()) {
            startIndex++;
        }
        return Arrays.stream(lines).skip(startIndex).collect(Collectors.joining("\n")).strip();
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
        WikiFrontmatterCodec.Frontmatter frontmatter = wikiFrontmatterCodec.parse(document.getBody());
        Optional<WikiAccessStats> stats = document.getKind() == WikiNodeKind.PAGE
                ? wikiAccessStatsPort.getStats(requireSpaceId(), document.getPath())
                : Optional.empty();
        return WikiPage.builder()
                .id(document.getId())
                .path(document.getPath())
                .parentPath(document.getParentPath())
                .title(document.getTitle())
                .slug(document.getSlug())
                .kind(document.getKind())
                .content(frontmatter.remainingBody())
                .createdAt(DATE_TIME_FORMATTER.format(document.getCreatedAt()))
                .updatedAt(DATE_TIME_FORMATTER.format(document.getUpdatedAt()))
                .revision(document.getRevision())
                .tags(frontmatter.tags())
                .summary(frontmatter.summary())
                .accessCount(stats.map(WikiAccessStats::getAccessCount).orElse(0L))
                .lastAccessedAt(stats.map(s -> DATE_TIME_FORMATTER.format(s.getLastAccessedAt())).orElse(null))
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
        List<ResolvedLink> links = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) {
            return links;
        }
        String scanned = stripFencedCodeBlocks(markdown);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\[[^\\]]+\\]\\(([^)]+)\\)")
                .matcher(scanned);
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

    private String stripFencedCodeBlocks(String markdown) {
        String[] lines = markdown.split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean insideFence = false;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (isFenceLine(line)) {
                insideFence = !insideFence;
                if (index < lines.length - 1) {
                    out.append('\n');
                }
                continue;
            }
            if (!insideFence) {
                out.append(line);
            }
            if (index < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    private boolean isFenceLine(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = line.stripLeading();
        return trimmed.startsWith("```") || trimmed.startsWith("~~~");
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
        String normalized = trimSlashes(Optional.ofNullable(path).orElse("")
                .replace('\\', '/')
                .trim());
        if (normalized.isBlank() || ".".equals(normalized)) {
            return "";
        }
        if (normalized.indexOf('\0') >= 0 || normalized.contains("..")) {
            throw new IllegalArgumentException("Path traversal is not allowed");
        }
        return normalized;
    }

    private String trimSlashes(String value) {
        int startIndex = 0;
        int endIndex = value.length();
        while (startIndex < endIndex && value.charAt(startIndex) == '/') {
            startIndex++;
        }
        while (endIndex > startIndex && value.charAt(endIndex - 1) == '/') {
            endIndex--;
        }
        return value.substring(startIndex, endIndex);
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
        String normalizedInput = Optional.ofNullable(input).orElse("")
                .trim()
                .toLowerCase(Locale.ROOT);
        StringBuilder slug = new StringBuilder();
        boolean previousWasSeparator = false;
        for (int index = 0; index < normalizedInput.length(); index++) {
            char currentChar = normalizedInput.charAt(index);
            if (isAsciiLowercaseLetterOrDigit(currentChar)) {
                slug.append(currentChar);
                previousWasSeparator = false;
                continue;
            }
            if (!previousWasSeparator && slug.length() > 0) {
                slug.append('-');
                previousWasSeparator = true;
            }
        }
        if (slug.length() > 0 && slug.charAt(slug.length() - 1) == '-') {
            slug.deleteCharAt(slug.length() - 1);
        }
        if (slug.length() == 0) {
            throw new IllegalArgumentException("Slug cannot be empty");
        }
        return slug.toString();
    }

    private boolean isAsciiLowercaseLetterOrDigit(char value) {
        return (value >= 'a' && value <= 'z') || (value >= '0' && value <= '9');
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
        List<String> tags;
        String summary;
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
        List<String> tags;
        String summary;
    }

    @Value
    @Builder
    public static class TransactionCommand {
        List<TxOpCommand> operations;
        String actor;
    }

    @Value
    @Builder
    public static class TxOpCommand {
        WikiTxOperationType op;
        String path;
        String parentPath;
        String slug;
        String title;
        String content;
        WikiNodeKind kind;
        String expectedRevision;
    }

    @Value
    @Builder
    public static class PatchPageCommand {
        String path;
        WikiPatchOperation operation;
        String heading;
        String content;
        String expectedRevision;
        String actor;
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
    public static class SearchCommand {
        String query;
        String mode;
        Integer limit;
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
