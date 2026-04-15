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

package me.golemcore.brain.adapter.out.filesystem;

import me.golemcore.brain.application.exception.WikiEditConflictException;
import me.golemcore.brain.application.exception.WikiNotFoundException;
import me.golemcore.brain.application.port.out.SpaceRepository;
import me.golemcore.brain.application.port.out.WikiRepository;
import me.golemcore.brain.application.space.SpaceContextHolder;
import me.golemcore.brain.config.WikiProperties;
import me.golemcore.brain.domain.WikiAsset;
import me.golemcore.brain.domain.WikiAssetContent;
import me.golemcore.brain.domain.WikiIndexedDocument;
import me.golemcore.brain.domain.WikiNodeKind;
import me.golemcore.brain.domain.WikiNodeReference;
import me.golemcore.brain.domain.WikiPageDocument;
import me.golemcore.brain.domain.WikiPageHistoryEntry;
import me.golemcore.brain.domain.WikiPageHistoryVersion;
import me.golemcore.brain.domain.space.Space;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Stores each wiki space as a directory tree of Markdown files and derived
 * metadata.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileSystemWikiRepository implements WikiRepository {

    private static final String INDEX_FILE_NAME = "index.md";
    private static final String ORDER_FILE_NAME = ".order.json";
    private static final String MARKDOWN_EXTENSION = ".md";
    private static final String HISTORY_METADATA_EXTENSION = ".meta";
    private static final String HISTORY_DIRECTORY_NAME = ".history";
    private static final DateTimeFormatter HISTORY_TIMESTAMP_FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmssSSS");

    private static final String SPACES_DIR = "spaces";

    private final WikiProperties wikiProperties;
    private final SpaceRepository spaceRepository;

    @PostConstruct
    @Override
    public void initialize() {
        Path spacesRoot = wikiProperties.getStorageRoot().resolve(SPACES_DIR);
        try {
            Files.createDirectories(spacesRoot);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize wiki spaces root", exception);
        }
        for (Space space : spaceRepository.listSpaces()) {
            initializeSpace(space.getId());
        }
    }

    @Override
    public void initializeSpace(String spaceId) {
        Path root = wikiProperties.getStorageRoot().resolve(SPACES_DIR).resolve(spaceId);
        try {
            Files.createDirectories(root);
            if (!Files.exists(root.resolve(INDEX_FILE_NAME))) {
                writeMarkdown(
                        root.resolve(INDEX_FILE_NAME),
                        renderMarkdown(
                                "Welcome",
                                "This lightweight wiki stores every page as markdown on disk.\n\n"
                                        + "- Create pages and sections\n"
                                        + "- Browse the content tree\n"
                                        + "- Search across notes\n"
                                        + "- Edit without a database"));
            }
            if (wikiProperties.isSeedDemoContent()) {
                seedDemoContent(root);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize space " + spaceId, exception);
        }
    }

    private Path spaceRoot() {
        return wikiProperties.getStorageRoot().resolve(SPACES_DIR).resolve(SpaceContextHolder.require());
    }

    @Override
    public Optional<WikiNodeReference> findReference(String path) {
        String normalizedPath = normalizePath(path);
        if (normalizedPath.isBlank()) {
            return Optional.of(getRootReference());
        }

        Path sectionPath = spaceRoot().resolve(normalizedPath).normalize();
        if (isMaterializableSectionDirectory(sectionPath)) {
            return Optional.of(buildSectionReference(sectionPath));
        }

        Path pagePath = spaceRoot().resolve(normalizedPath + MARKDOWN_EXTENSION).normalize();
        if (Files.isRegularFile(pagePath)) {
            return Optional.of(buildPageReference(pagePath));
        }
        return Optional.empty();
    }

    @Override
    public WikiNodeReference getRootReference() {
        return WikiNodeReference.builder()
                .id("root")
                .path("")
                .parentPath(null)
                .slug("")
                .kind(WikiNodeKind.ROOT)
                .nodePath(spaceRoot())
                .parentDirectory(null)
                .markdownPath(spaceRoot().resolve(INDEX_FILE_NAME))
                .build();
    }

    @Override
    public List<WikiNodeReference> listChildren(WikiNodeReference parentReference) {
        Path directory = parentReference.getNodePath();
        List<WikiNodeReference> references = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path candidate : stream) {
                if (isMaterializableSectionDirectory(candidate)) {
                    references.add(buildSectionReference(candidate));
                }
                if (Files.isRegularFile(candidate)
                        && candidate.getFileName().toString().endsWith(MARKDOWN_EXTENSION)
                        && !INDEX_FILE_NAME.equals(candidate.getFileName().toString())) {
                    references.add(buildPageReference(candidate));
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read child pages", exception);
        }

        references.sort(Comparator.comparing(WikiNodeReference::getSlug, String.CASE_INSENSITIVE_ORDER));
        // User-controlled order is stored separately so Markdown file names remain
        // stable and easy to edit outside the application.
        List<String> orderedSlugs = readOrderedSlugs(directory);
        List<WikiNodeReference> orderedReferences = new ArrayList<>();
        for (String orderedSlug : orderedSlugs) {
            for (WikiNodeReference reference : references) {
                if (reference.getSlug().equals(orderedSlug) && !orderedReferences.contains(reference)) {
                    orderedReferences.add(reference);
                }
            }
        }
        for (WikiNodeReference reference : references) {
            if (!orderedReferences.contains(reference)) {
                orderedReferences.add(reference);
            }
        }
        return orderedReferences;
    }

    @Override
    public WikiPageDocument readDocument(WikiNodeReference nodeReference) {
        if (nodeReference.getKind() == WikiNodeKind.SECTION && !safeExists(nodeReference.getMarkdownPath())) {
            return synthesizeSectionDocument(nodeReference);
        }
        return readDocument(nodeReference.getMarkdownPath(), nodeReference);
    }

    @Override
    public WikiPageDocument createPage(String parentPath, String title, String slug, String content,
            WikiNodeKind kind) {
        WikiNodeReference parentReference = requireSectionReference(parentPath);
        String requestedSlug = Optional.ofNullable(slug).orElse("");
        String resolvedSlug = slugify(requestedSlug.isBlank() ? title : requestedSlug);
        Path targetPath = buildNodePath(parentReference.getNodePath(), resolvedSlug, kind);
        requireAvailable(targetPath, resolvedSlug);

        try {
            if (kind == WikiNodeKind.SECTION) {
                Files.createDirectories(targetPath);
                writeMarkdown(targetPath.resolve(INDEX_FILE_NAME), renderMarkdown(title, content));
            } else if (kind == WikiNodeKind.PAGE) {
                writeMarkdown(targetPath, renderMarkdown(title, content));
            } else {
                throw new IllegalArgumentException("Unsupported node kind: " + kind);
            }
            saveOrderedSlugs(parentReference.getNodePath(),
                    insertSlug(readOrderedSlugs(parentReference.getNodePath()), resolvedSlug, null));
            return readDocument(findReference(joinPath(parentReference.getPath(), resolvedSlug)).orElseThrow());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create page", exception);
        }
    }

    @Override
    public WikiPageDocument updatePage(String path, String title, String slug, String content, String expectedRevision,
            String actor, String reason, String summary) {
        WikiNodeReference nodeReference = findReference(path)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(path)));
        WikiPageDocument currentDocument = readDocument(nodeReference);
        if (expectedRevision != null
                && !expectedRevision.isBlank()
                && !expectedRevision.equals(currentDocument.getRevision())) {
            throw new WikiEditConflictException(expectedRevision, currentDocument);
        }
        String normalizedTitle = requireTitle(title);
        String requestedSlug = Optional.ofNullable(slug).orElse("");
        String nextSlug = nodeReference.getKind() == WikiNodeKind.ROOT
                ? nodeReference.getSlug()
                : slugify(requestedSlug.isBlank() ? normalizedTitle : requestedSlug);
        String nextPath = nodeReference.getPath();

        try {
            snapshotHistory(nodeReference, actor, reason, summary);
            String previousPath = nodeReference.getPath();
            if (nodeReference.getKind() != WikiNodeKind.ROOT && !nodeReference.getSlug().equals(nextSlug)) {
                nextPath = renameNode(nodeReference, nextSlug);
                nodeReference = findReference(nextPath)
                        .orElseThrow(() -> new IllegalStateException("Page missing after rename"));
            }
            writeMarkdown(nodeReference.getMarkdownPath(),
                    renderMarkdown(normalizedTitle, rewriteAssetPathReferences(content, previousPath, nextPath)));
            rewriteAssetReferencesInSubtree(nodeReference, previousPath, nextPath);
            return readDocument(nodeReference);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to update page", exception);
        }
    }

    @Override
    public void deletePage(String path) {
        WikiNodeReference nodeReference = findReference(path)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(path)));
        if (nodeReference.getKind() == WikiNodeKind.ROOT) {
            throw new IllegalArgumentException("Root page cannot be deleted");
        }
        try {
            deleteRecursively(nodeReference.getNodePath());
            if (nodeReference.getKind() == WikiNodeKind.PAGE) {
                deleteIfExistsRecursively(getAssetsDirectory(nodeReference));
                deleteIfExistsRecursively(getHistoryDirectory(nodeReference));
            }
            List<String> updatedOrder = new ArrayList<>(readOrderedSlugs(nodeReference.getParentDirectory()));
            updatedOrder.remove(nodeReference.getSlug());
            saveOrderedSlugs(nodeReference.getParentDirectory(), updatedOrder);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete page", exception);
        }
    }

    @Override
    public WikiPageDocument movePage(String path, String targetParentPath, String targetSlug, String beforeSlug) {
        WikiNodeReference sourceReference = findReference(path)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(path)));
        if (sourceReference.getKind() == WikiNodeKind.ROOT) {
            throw new IllegalArgumentException("Root page cannot be moved");
        }

        WikiNodeReference targetParentReference = requireSectionReference(targetParentPath);
        String requestedSlug = Optional.ofNullable(targetSlug).orElse("");
        String resolvedTargetSlug = slugify(requestedSlug.isBlank() ? sourceReference.getSlug() : requestedSlug);
        Path targetNodePath = buildNodePath(targetParentReference.getNodePath(), resolvedTargetSlug,
                sourceReference.getKind());
        boolean sameNode = sourceReference.getNodePath().equals(targetNodePath);
        if (sourceReference.getKind() == WikiNodeKind.SECTION
                && targetParentReference.getNodePath().startsWith(sourceReference.getNodePath())) {
            throw new IllegalArgumentException("A section cannot be moved into itself");
        }
        if (!sameNode) {
            requireAvailable(targetNodePath, resolvedTargetSlug);
        }

        try {
            if (!sameNode) {
                Files.move(sourceReference.getNodePath(), targetNodePath, StandardCopyOption.REPLACE_EXISTING);
                movePageSidecars(sourceReference, targetParentReference, resolvedTargetSlug);
            }
            List<String> sourceOrder = new ArrayList<>(readOrderedSlugs(sourceReference.getParentDirectory()));
            sourceOrder.remove(sourceReference.getSlug());
            saveOrderedSlugs(sourceReference.getParentDirectory(), sourceOrder);

            List<String> targetOrder = new ArrayList<>(readOrderedSlugs(targetParentReference.getNodePath()));
            if (sameNode) {
                targetOrder.remove(sourceReference.getSlug());
            }
            saveOrderedSlugs(targetParentReference.getNodePath(),
                    insertSlug(targetOrder, resolvedTargetSlug, beforeSlug));
            WikiNodeReference targetReference = findReference(
                    joinPath(targetParentReference.getPath(), resolvedTargetSlug)).orElseThrow();
            rewriteAssetReferencesInSubtree(targetReference, sourceReference.getPath(), targetReference.getPath());
            return readDocument(targetReference);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to move page", exception);
        }
    }

    @Override
    public WikiPageDocument copyPage(String path, String targetParentPath, String targetSlug, String beforeSlug) {
        WikiNodeReference sourceReference = findReference(path)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(path)));
        if (sourceReference.getKind() == WikiNodeKind.ROOT) {
            throw new IllegalArgumentException("Root page cannot be copied");
        }
        WikiNodeReference targetParentReference = requireSectionReference(targetParentPath);
        String requestedSlug = Optional.ofNullable(targetSlug).orElse("");
        String resolvedTargetSlug = slugify(
                requestedSlug.isBlank() ? sourceReference.getSlug() + "-copy" : requestedSlug);
        Path targetNodePath = buildNodePath(targetParentReference.getNodePath(), resolvedTargetSlug,
                sourceReference.getKind());
        requireAvailable(targetNodePath, resolvedTargetSlug);

        try {
            copyRecursively(sourceReference.getNodePath(), targetNodePath);
            copyPageSidecars(sourceReference, targetParentReference, resolvedTargetSlug);
            saveOrderedSlugs(targetParentReference.getNodePath(),
                    insertSlug(readOrderedSlugs(targetParentReference.getNodePath()), resolvedTargetSlug, beforeSlug));
            WikiNodeReference targetReference = findReference(
                    joinPath(targetParentReference.getPath(), resolvedTargetSlug)).orElseThrow();
            rewriteAssetReferencesInSubtree(targetReference, sourceReference.getPath(), targetReference.getPath());
            return readDocument(targetReference);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to copy page", exception);
        }
    }

    @Override
    public WikiPageDocument convertPage(String path, WikiNodeKind targetKind) {
        WikiNodeReference sourceReference = findReference(path)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(path)));
        if (sourceReference.getKind() == WikiNodeKind.ROOT) {
            throw new IllegalArgumentException("Root page cannot be converted");
        }
        if (targetKind != WikiNodeKind.PAGE && targetKind != WikiNodeKind.SECTION) {
            throw new IllegalArgumentException("Target kind must be PAGE or SECTION");
        }
        if (sourceReference.getKind() == targetKind) {
            return readDocument(sourceReference);
        }
        try {
            if (targetKind == WikiNodeKind.SECTION) {
                return convertPageToSection(sourceReference);
            }
            return convertSectionToPage(sourceReference);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to convert page", exception);
        }
    }

    @Override
    public void sortChildren(String path, List<String> orderedSlugs) {
        WikiNodeReference sectionReference = requireSectionReference(path);
        List<WikiNodeReference> children = listChildren(sectionReference);
        Set<String> existingSlugs = children.stream().map(WikiNodeReference::getSlug)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> requestedOrder = Optional.ofNullable(orderedSlugs).orElse(List.of()).stream()
                .filter(existingSlugs::contains)
                .distinct()
                .toList();
        List<String> mergedOrder = new ArrayList<>(requestedOrder);
        for (String slug : existingSlugs) {
            if (!mergedOrder.contains(slug)) {
                mergedOrder.add(slug);
            }
        }
        saveOrderedSlugs(sectionReference.getNodePath(), mergedOrder);
    }

    @Override
    public List<WikiNodeReference> flatten() {
        List<WikiNodeReference> references = new ArrayList<>();
        flattenRecursively(getRootReference(), references);
        return references;
    }

    @Override
    public List<WikiIndexedDocument> listDocuments(String spaceId) {
        return withSpace(spaceId, () -> flatten().stream().map(this::toIndexedDocument).toList());
    }

    @Override
    public List<WikiPageHistoryEntry> listPageHistory(String path) {
        WikiNodeReference nodeReference = findReference(path)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(path)));
        Path historyDirectory = getHistoryDirectory(nodeReference);
        if (!Files.exists(historyDirectory)) {
            return List.of();
        }
        try {
            List<WikiPageHistoryEntry> entries = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(historyDirectory, "*.md")) {
                for (Path historyPath : stream) {
                    entries.add(readHistoryEntry(historyPath));
                }
            }
            entries.sort(Comparator.comparing(WikiPageHistoryEntry::getRecordedAt).reversed());
            return entries;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list page history", exception);
        }
    }

    @Override
    public WikiPageDocument restorePageVersion(String path, String versionId, String actor, String reason,
            String summary) {
        WikiNodeReference nodeReference = findReference(path)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(path)));
        Path historyPath = getHistoryDirectory(nodeReference).resolve(versionId + ".md");
        if (!Files.exists(historyPath)) {
            throw new WikiNotFoundException("History version not found: " + versionId);
        }
        try {
            snapshotHistory(nodeReference, actor, reason, summary);
            String rawContent = Files.readString(historyPath, StandardCharsets.UTF_8);
            writeMarkdown(nodeReference.getMarkdownPath(), rawContent);
            return readDocument(nodeReference);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to restore page history", exception);
        }
    }

    @Override
    public WikiPageHistoryVersion readPageHistoryVersion(String path, String versionId) {
        WikiNodeReference nodeReference = findReference(path)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(path)));
        Path historyPath = getHistoryDirectory(nodeReference).resolve(versionId + ".md");
        if (!Files.exists(historyPath)) {
            throw new WikiNotFoundException("History version not found: " + versionId);
        }
        try {
            String rawContent = Files.readString(historyPath, StandardCharsets.UTF_8);
            List<String> lines = Arrays.asList(rawContent.split("\\R", -1));
            HistoryMetadata metadata = readHistoryMetadata(historyPath);
            return WikiPageHistoryVersion.builder()
                    .id(versionId)
                    .title(deriveTitleFromLines(historyPath, lines))
                    .slug(nodeReference.getSlug())
                    .content(deriveBody(lines))
                    .recordedAt(metadata.recordedAt())
                    .author(metadata.author())
                    .reason(metadata.reason())
                    .summary(metadata.summary())
                    .build();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read page history version", exception);
        }
    }

    @Override
    public List<WikiAsset> listAssets(String path) {
        WikiNodeReference nodeReference = findReference(path)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(path)));
        Path assetsDirectory = getAssetsDirectory(nodeReference);
        if (!Files.exists(assetsDirectory)) {
            return List.of();
        }
        List<WikiAsset> assets = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(assetsDirectory)) {
            for (Path assetPath : stream) {
                if (!Files.isRegularFile(assetPath)) {
                    continue;
                }
                assets.add(toAsset(assetPath, nodeReference));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list assets", exception);
        }
        assets.sort(Comparator.comparing(WikiAsset::getName, String.CASE_INSENSITIVE_ORDER));
        return assets;
    }

    @Override
    public WikiAsset saveAsset(String path, String fileName, String contentType, InputStream inputStream) {
        WikiNodeReference nodeReference = findReference(path)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(path)));
        Path assetsDirectory = getAssetsDirectory(nodeReference);
        String safeFileName = sanitizeFileName(fileName);
        String storedFileName = ensureUniqueAssetName(assetsDirectory, safeFileName);
        Path targetPath = assetsDirectory.resolve(storedFileName);
        try {
            Files.createDirectories(assetsDirectory);
            Files.copy(inputStream, targetPath);
            return toAsset(targetPath, nodeReference,
                    Optional.ofNullable(contentType).orElseGet(() -> probeContentType(targetPath)));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to save asset", exception);
        }
    }

    @Override
    public WikiAsset renameAsset(String path, String oldName, String newName) {
        WikiNodeReference nodeReference = findReference(path)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(path)));
        Path assetsDirectory = getAssetsDirectory(nodeReference);
        Path sourcePath = assetsDirectory.resolve(sanitizeFileName(oldName));
        if (!Files.exists(sourcePath)) {
            throw new WikiNotFoundException("Asset not found: " + oldName);
        }
        String safeNewName = sanitizeFileName(newName);
        Path targetPath = assetsDirectory.resolve(safeNewName);
        if (Files.exists(targetPath)) {
            throw new IllegalArgumentException("An asset with this name already exists: " + newName);
        }
        try {
            String previousAssetPath = toAsset(sourcePath, nodeReference).getPath();
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            WikiAsset renamedAsset = toAsset(targetPath, nodeReference);
            rewriteAssetNameReferences(nodeReference.getMarkdownPath(), previousAssetPath, renamedAsset.getPath(),
                    nodeReference.getPath(), sanitizeFileName(oldName), safeNewName);
            return renamedAsset;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to rename asset", exception);
        }
    }

    @Override
    public WikiAssetContent openAsset(String path, String assetName) {
        WikiNodeReference nodeReference = findReference(path)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(path)));
        Path assetPath = resolveAssetPath(nodeReference, sanitizeFileName(assetName));
        try {
            return WikiAssetContent.builder()
                    .name(assetPath.getFileName().toString())
                    .contentType(probeContentType(assetPath))
                    .size(Files.size(assetPath))
                    .inputStream(Files.newInputStream(assetPath))
                    .build();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to open asset", exception);
        }
    }

    @Override
    public void deleteAsset(String path, String assetName) {
        WikiNodeReference nodeReference = findReference(path)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(path)));
        Path assetPath = getAssetsDirectory(nodeReference).resolve(sanitizeFileName(assetName));
        try {
            Files.deleteIfExists(assetPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete asset", exception);
        }
    }

    private void snapshotHistory(WikiNodeReference nodeReference, String actor, String reason, String summary)
            throws IOException {
        if (!nodeReference.getKind().keepsHistory() || !safeExists(nodeReference.getMarkdownPath())) {
            return;
        }
        Path markdownPath = requireSpaceContainedPath(nodeReference.getMarkdownPath());
        Path historyDirectory = requireSpaceContainedPath(getHistoryDirectory(nodeReference));
        Files.createDirectories(historyDirectory);
        String versionId = Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8);
        Path historyPath = requireSpaceContainedPath(historyDirectory.resolve(versionId + ".md"));
        Files.copy(markdownPath, historyPath, StandardCopyOption.REPLACE_EXISTING);
        writeHistoryMetadata(historyPath, new HistoryMetadata(
                Instant.now().toString(),
                Optional.ofNullable(actor).filter(value -> !value.isBlank()).orElse("Local editor"),
                Optional.ofNullable(reason).filter(value -> !value.isBlank()).orElse("Page updated"),
                Optional.ofNullable(summary).orElse("")));
    }

    private WikiPageHistoryEntry readHistoryEntry(Path historyPath) throws IOException {
        String fileName = historyPath.getFileName().toString();
        String versionId = fileName.substring(0, fileName.length() - MARKDOWN_EXTENSION.length());
        List<String> lines = Files.readAllLines(historyPath, StandardCharsets.UTF_8);
        String title = deriveTitleFromLines(historyPath, lines);
        String slug = historyPath.getParent().getParent().getFileName().toString();
        HistoryMetadata metadata = readHistoryMetadata(historyPath);
        return WikiPageHistoryEntry.builder()
                .id(versionId)
                .title(title)
                .slug(slug)
                .recordedAt(metadata.recordedAt())
                .author(metadata.author())
                .reason(metadata.reason())
                .summary(metadata.summary())
                .build();
    }

    private Path getHistoryDirectory(WikiNodeReference nodeReference) {
        Path containerDirectory = nodeReference.getKind().isContainer() ? nodeReference.getNodePath()
                : nodeReference.getParentDirectory();
        String slug = nodeReference.getKind().isContainer() ? ".section-history-" + nodeReference.getSlug()
                : ".history-" + nodeReference.getSlug();
        return containerDirectory.resolve(HISTORY_DIRECTORY_NAME).resolve(slug);
    }

    private void flattenRecursively(WikiNodeReference nodeReference, List<WikiNodeReference> references) {
        references.add(nodeReference);
        if (nodeReference.getKind() != WikiNodeKind.PAGE) {
            for (WikiNodeReference childReference : listChildren(nodeReference)) {
                flattenRecursively(childReference, references);
            }
        }
    }

    private <T> T withSpace(String spaceId, java.util.function.Supplier<T> supplier) {
        String previousSpaceId = SpaceContextHolder.get();
        SpaceContextHolder.set(spaceId);
        try {
            return supplier.get();
        } finally {
            restorePreviousSpace(previousSpaceId);
        }
    }

    private void restorePreviousSpace(String previousSpaceId) {
        if (previousSpaceId == null || previousSpaceId.isBlank()) {
            SpaceContextHolder.clear();
            return;
        }
        SpaceContextHolder.set(previousSpaceId);
    }

    private WikiIndexedDocument toIndexedDocument(WikiNodeReference nodeReference) {
        WikiPageDocument document = readDocument(nodeReference);
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

    private WikiNodeReference requireSectionReference(String path) {
        WikiNodeReference nodeReference = findReference(path)
                .orElseThrow(() -> new WikiNotFoundException("Section not found: " + normalizePath(path)));
        if (!nodeReference.getKind().isContainer()) {
            throw new IllegalArgumentException("Target path is not a section");
        }
        return nodeReference;
    }

    private boolean isMaterializableSectionDirectory(Path path) {
        Path safePath = requireSpaceContainedPath(path);
        Path normalizedRoot = spaceRoot().toAbsolutePath().normalize();
        return Files.isDirectory(safePath) && !hasHiddenPathSegment(normalizedRoot.relativize(safePath));
    }

    private Path requireSpaceContainedPath(Path path) {
        Path root = spaceRoot().toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new IllegalArgumentException("Path traversal is not allowed");
        }
        return normalized;
    }

    private boolean safeExists(Path path) {
        return Files.exists(requireSpaceContainedPath(path));
    }

    private boolean hasHiddenPathSegment(Path relativePath) {
        for (Path segment : relativePath) {
            if (segment.getFileName().toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private WikiNodeReference buildSectionReference(Path sectionPath) {
        Path relativePath = spaceRoot().relativize(sectionPath);
        String path = normalizePath(relativePath.toString());
        String parentPath = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
        return WikiNodeReference.builder()
                .id(idForPath(path))
                .path(path)
                .parentPath(parentPath)
                .slug(sectionPath.getFileName().toString())
                .kind(WikiNodeKind.SECTION)
                .nodePath(sectionPath)
                .parentDirectory(sectionPath.getParent())
                .markdownPath(sectionPath.resolve(INDEX_FILE_NAME))
                .build();
    }

    private WikiNodeReference buildPageReference(Path pagePath) {
        Path relativePath = spaceRoot().relativize(pagePath);
        String slug = pagePath.getFileName().toString().replace(MARKDOWN_EXTENSION, "");
        Path parentPath = relativePath.getParent();
        String normalizedParentPath = parentPath == null ? "" : normalizePath(parentPath.toString());
        String path = joinPath(normalizedParentPath, slug);
        return WikiNodeReference.builder()
                .id(idForPath(path))
                .path(path)
                .parentPath(normalizedParentPath)
                .slug(slug)
                .kind(WikiNodeKind.PAGE)
                .nodePath(pagePath)
                .parentDirectory(pagePath.getParent())
                .markdownPath(pagePath)
                .build();
    }

    private WikiPageDocument synthesizeSectionDocument(WikiNodeReference nodeReference) {
        Path nodePath = requireSpaceContainedPath(nodeReference.getNodePath());
        return WikiPageDocument.builder()
                .id(nodeReference.getId())
                .path(nodeReference.getPath())
                .parentPath(nodeReference.getParentPath())
                .slug(nodeReference.getSlug())
                .title(humanizeSlug(nodeReference.getSlug()))
                .body("")
                .kind(WikiNodeKind.SECTION)
                .createdAt(readCreatedInstant(nodePath))
                .updatedAt(readUpdatedInstant(nodePath))
                .revision(calculateRevision(""))
                .build();
    }

    private WikiPageDocument readDocument(Path markdownPath, WikiNodeReference nodeReference) {
        try {
            String rawContent = Files.readString(markdownPath, StandardCharsets.UTF_8);
            List<String> lines = Arrays.asList(rawContent.split("\\R", -1));
            String title = lines.isEmpty() ? deriveTitle(markdownPath) : deriveTitleFromLines(markdownPath, lines);
            String body = lines.isEmpty() ? "" : deriveBody(lines);
            return WikiPageDocument.builder()
                    .id(nodeReference.getId())
                    .path(nodeReference.getPath())
                    .parentPath(nodeReference.getParentPath())
                    .slug(nodeReference.getSlug())
                    .title(title)
                    .body(body)
                    .kind(nodeReference.getKind())
                    .createdAt(readCreatedInstant(markdownPath))
                    .updatedAt(readUpdatedInstant(markdownPath))
                    .revision(calculateRevision(rawContent))
                    .build();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read markdown file " + markdownPath, exception);
        }
    }

    private String calculateRevision(String rawContent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawContent.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void writeHistoryMetadata(Path historyPath, HistoryMetadata metadata) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("recordedAt", metadata.recordedAt());
        properties.setProperty("author", metadata.author());
        properties.setProperty("reason", metadata.reason());
        properties.setProperty("summary", metadata.summary());
        try (Writer writer = Files.newBufferedWriter(getHistoryMetadataPath(historyPath), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            properties.store(writer, null);
        }
    }

    private HistoryMetadata readHistoryMetadata(Path historyPath) throws IOException {
        Path metadataPath = getHistoryMetadataPath(historyPath);
        if (!Files.exists(metadataPath)) {
            return new HistoryMetadata(
                    Files.getLastModifiedTime(historyPath).toInstant().toString(),
                    "Local editor",
                    "Page updated",
                    "");
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(metadataPath, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return new HistoryMetadata(
                properties.getProperty("recordedAt", Files.getLastModifiedTime(historyPath).toInstant().toString()),
                properties.getProperty("author", "Local editor"),
                properties.getProperty("reason", "Page updated"),
                properties.getProperty("summary", ""));
    }

    private Path getHistoryMetadataPath(Path historyPath) {
        String fileName = historyPath.getFileName().toString();
        String versionId = fileName.substring(0, fileName.length() - MARKDOWN_EXTENSION.length());
        return historyPath.getParent().resolve(versionId + HISTORY_METADATA_EXTENSION);
    }

    private String deriveTitleFromLines(Path markdownPath, List<String> lines) {
        String firstLine = lines.getFirst();
        if (firstLine.startsWith("# ")) {
            String title = firstLine.substring(2).trim();
            return title.isBlank() ? deriveTitle(markdownPath) : title;
        }
        return deriveTitle(markdownPath);
    }

    private String deriveBody(List<String> lines) {
        String firstLine = lines.getFirst();
        if (firstLine.startsWith("# ")) {
            return lines.stream().skip(2).collect(Collectors.joining("\n")).strip();
        }
        return String.join("\n", lines).strip();
    }

    private String renameNode(WikiNodeReference nodeReference, String nextSlug) throws IOException {
        Path targetPath = buildNodePath(nodeReference.getParentDirectory(), nextSlug, nodeReference.getKind());
        requireAvailable(targetPath, nextSlug);
        Files.move(nodeReference.getNodePath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        if (nodeReference.getKind() == WikiNodeKind.PAGE) {
            moveIfExists(getAssetsDirectory(nodeReference),
                    nodeReference.getParentDirectory().resolve(".assets-" + nextSlug));
            moveIfExists(getHistoryDirectory(nodeReference),
                    nodeReference.getParentDirectory().resolve(HISTORY_DIRECTORY_NAME).resolve(".history-" + nextSlug));
        } else if (nodeReference.getKind() == WikiNodeKind.SECTION) {
            moveIfExists(
                    targetPath.resolve(HISTORY_DIRECTORY_NAME).resolve(".section-history-" + nodeReference.getSlug()),
                    targetPath.resolve(HISTORY_DIRECTORY_NAME).resolve(".section-history-" + nextSlug));
        }
        List<String> updatedOrder = new ArrayList<>(readOrderedSlugs(nodeReference.getParentDirectory()));
        int index = updatedOrder.indexOf(nodeReference.getSlug());
        if (index >= 0) {
            updatedOrder.set(index, nextSlug);
        }
        saveOrderedSlugs(nodeReference.getParentDirectory(), updatedOrder);
        return joinPath(nodeReference.getParentPath(), nextSlug);
    }

    private WikiPageDocument convertPageToSection(WikiNodeReference sourceReference) throws IOException {
        if (sourceReference.getKind() != WikiNodeKind.PAGE) {
            throw new IllegalArgumentException("Only pages can be converted to sections");
        }
        Path targetDirectory = sourceReference.getParentDirectory().resolve(sourceReference.getSlug());
        requireAvailable(targetDirectory, sourceReference.getSlug());
        Files.createDirectories(targetDirectory);
        Files.move(sourceReference.getMarkdownPath(), targetDirectory.resolve(INDEX_FILE_NAME),
                StandardCopyOption.REPLACE_EXISTING);
        moveIfExists(getAssetsDirectory(sourceReference), targetDirectory.resolve(".section-assets"));
        moveIfExists(
                getHistoryDirectory(sourceReference),
                targetDirectory.resolve(HISTORY_DIRECTORY_NAME)
                        .resolve(".section-history-" + sourceReference.getSlug()));
        return readDocument(findReference(sourceReference.getPath()).orElseThrow());
    }

    private WikiPageDocument convertSectionToPage(WikiNodeReference sourceReference) throws IOException {
        if (sourceReference.getKind() != WikiNodeKind.SECTION) {
            throw new IllegalArgumentException("Only sections can be converted to pages");
        }
        if (!listChildren(sourceReference).isEmpty()) {
            throw new IllegalArgumentException("Only empty sections can be converted to pages");
        }
        Path targetMarkdownPath = sourceReference.getParentDirectory()
                .resolve(sourceReference.getSlug() + MARKDOWN_EXTENSION);
        requireAvailable(targetMarkdownPath, sourceReference.getSlug());
        Path safeTargetMarkdownPath = requireSpaceContainedPath(targetMarkdownPath);
        if (safeExists(sourceReference.getMarkdownPath())) {
            Path safeSourceMarkdownPath = requireSpaceContainedPath(sourceReference.getMarkdownPath());
            Files.move(safeSourceMarkdownPath, safeTargetMarkdownPath, StandardCopyOption.REPLACE_EXISTING);
        } else {
            writeMarkdown(safeTargetMarkdownPath, renderMarkdown(humanizeSlug(sourceReference.getSlug()), ""));
        }
        moveIfExists(getAssetsDirectory(sourceReference),
                sourceReference.getParentDirectory().resolve(".assets-" + sourceReference.getSlug()));
        moveIfExists(
                getHistoryDirectory(sourceReference),
                sourceReference.getParentDirectory().resolve(HISTORY_DIRECTORY_NAME)
                        .resolve(".history-" + sourceReference.getSlug()));
        deleteIfExistsRecursively(sourceReference.getNodePath());
        return readDocument(findReference(sourceReference.getPath()).orElseThrow());
    }

    private void movePageSidecars(WikiNodeReference sourceReference, WikiNodeReference targetParentReference,
            String targetSlug) throws IOException {
        if (sourceReference.getKind() != WikiNodeKind.PAGE) {
            return;
        }
        moveIfExists(getAssetsDirectory(sourceReference),
                targetParentReference.getNodePath().resolve(".assets-" + targetSlug));
        moveIfExists(getHistoryDirectory(sourceReference),
                targetParentReference.getNodePath().resolve(HISTORY_DIRECTORY_NAME).resolve(".history-" + targetSlug));
    }

    private void copyPageSidecars(WikiNodeReference sourceReference, WikiNodeReference targetParentReference,
            String targetSlug) throws IOException {
        if (sourceReference.getKind() != WikiNodeKind.PAGE) {
            return;
        }
        copyIfExists(getAssetsDirectory(sourceReference),
                targetParentReference.getNodePath().resolve(".assets-" + targetSlug));
    }

    private void moveIfExists(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        Files.createDirectories(target.getParent());
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private void copyIfExists(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        Files.createDirectories(target.getParent());
        copyRecursively(source, target);
    }

    private void deleteIfExistsRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        deleteRecursively(path);
    }

    private void rewriteAssetReferencesInSubtree(WikiNodeReference rootReference, String oldRootPath,
            String newRootPath) throws IOException {
        String normalizedOldRootPath = normalizePath(oldRootPath);
        String normalizedNewRootPath = normalizePath(newRootPath);
        if (normalizedOldRootPath.equals(normalizedNewRootPath)) {
            return;
        }
        rewriteAssetReferencesForReference(rootReference, normalizedOldRootPath, normalizedNewRootPath);
        if (rootReference.getKind() == WikiNodeKind.PAGE) {
            return;
        }
        rewriteAssetReferencesForChildren(rootReference.getNodePath(), normalizedOldRootPath, normalizedNewRootPath);
    }

    private void rewriteAssetReferencesForChildren(Path directory, String oldRootPath, String newRootPath)
            throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path candidate : stream) {
                String fileName = candidate.getFileName().toString();
                if (fileName.startsWith(".")) {
                    continue;
                }
                if (isMaterializableSectionDirectory(candidate)) {
                    WikiNodeReference childReference = buildSectionReference(candidate);
                    rewriteAssetReferencesForReference(childReference, oldRootPath, newRootPath);
                    rewriteAssetReferencesForChildren(candidate, oldRootPath, newRootPath);
                    continue;
                }
                if (Files.isRegularFile(candidate)
                        && fileName.endsWith(MARKDOWN_EXTENSION)
                        && !INDEX_FILE_NAME.equals(fileName)) {
                    rewriteAssetReferencesForReference(buildPageReference(candidate), oldRootPath, newRootPath);
                }
            }
        }
    }

    private void rewriteAssetReferencesForReference(WikiNodeReference reference, String oldRootPath, String newRootPath)
            throws IOException {
        String previousPath = previousPathForReference(reference.getPath(), oldRootPath, newRootPath);
        rewriteMarkdownAssetPath(reference.getMarkdownPath(), previousPath, reference.getPath());
    }

    private String previousPathForReference(String currentPath, String oldRootPath, String newRootPath) {
        if (currentPath.equals(newRootPath)) {
            return oldRootPath;
        }
        if (!newRootPath.isBlank() && currentPath.startsWith(newRootPath + "/")) {
            return joinPath(oldRootPath, currentPath.substring(newRootPath.length() + 1));
        }
        return currentPath;
    }

    private void rewriteMarkdownAssetPath(Path markdownPath, String oldPath, String newPath) throws IOException {
        Path safeMarkdownPath = requireSpaceContainedPath(markdownPath);
        if (!Files.exists(safeMarkdownPath)) {
            return;
        }
        String rawMarkdown = Files.readString(safeMarkdownPath, StandardCharsets.UTF_8);
        String updatedMarkdown = rewriteAssetPathReferences(rawMarkdown, oldPath, newPath);
        if (!rawMarkdown.equals(updatedMarkdown)) {
            Files.writeString(safeMarkdownPath, updatedMarkdown, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }
    }

    private String rewriteAssetPathReferences(String content, String oldPath, String newPath) {
        String normalizedOldPath = normalizePath(oldPath);
        String normalizedNewPath = normalizePath(newPath);
        if (normalizedOldPath.equals(normalizedNewPath)) {
            return content;
        }
        return content
                .replace(assetPathPrefix(normalizedOldPath), assetPathPrefix(normalizedNewPath))
                .replace(unencodedAssetPathPrefix(normalizedOldPath), assetPathPrefix(normalizedNewPath))
                .replace(legacyAssetPathPrefix(normalizedOldPath), assetPathPrefix(normalizedNewPath))
                .replace(encodedLegacyAssetPathPrefix(normalizedOldPath), assetPathPrefix(normalizedNewPath));
    }

    private void rewriteAssetNameReferences(Path markdownPath, String oldAssetPath, String newAssetPath,
            String pagePath, String oldName, String newName) throws IOException {
        String normalizedPagePath = normalizePath(pagePath);
        String encodedOldName = encodeUrlComponent(oldName);
        String rawMarkdown = Files.readString(markdownPath, StandardCharsets.UTF_8);
        String updatedMarkdown = rawMarkdown
                .replace(oldAssetPath, newAssetPath)
                .replace(unencodedAssetPathPrefix(normalizedPagePath) + oldName, newAssetPath)
                .replace(unencodedAssetPathPrefix(normalizedPagePath) + encodedOldName, newAssetPath)
                .replace(assetPathPrefix(normalizedPagePath) + oldName, newAssetPath)
                .replace(assetPathPrefix(normalizedPagePath) + encodedOldName, newAssetPath)
                .replace(legacyAssetPathPrefix(normalizedPagePath) + oldName, newAssetPath)
                .replace(legacyAssetPathPrefix(normalizedPagePath) + encodedOldName, newAssetPath)
                .replace(encodedLegacyAssetPathPrefix(normalizedPagePath) + oldName, newAssetPath)
                .replace(encodedLegacyAssetPathPrefix(normalizedPagePath) + encodedOldName, newAssetPath)
                .replace("[" + oldName + "](", "[" + newName + "](");
        if (!rawMarkdown.equals(updatedMarkdown)) {
            Files.writeString(markdownPath, updatedMarkdown, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }
    }

    private void requireAvailable(Path targetPath, String slug) {
        if (Files.exists(targetPath)) {
            throw new IllegalArgumentException("A page with this slug already exists: " + slug);
        }
    }

    private Path buildNodePath(Path parentDirectory, String slug, WikiNodeKind kind) {
        if (kind.isContainer()) {
            return parentDirectory.resolve(slug);
        }
        return parentDirectory.resolve(slug + MARKDOWN_EXTENSION);
    }

    private List<String> readOrderedSlugs(Path directory) {
        Path orderFile = directory.resolve(ORDER_FILE_NAME);
        if (!Files.exists(orderFile)) {
            return List.of();
        }
        try {
            return parseStringArray(Files.readString(orderFile, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            log.warn("Failed to read order file {}", orderFile, exception);
            return List.of();
        }
    }

    private void saveOrderedSlugs(Path directory, List<String> orderedSlugs) {
        Set<String> uniqueSlugs = new LinkedHashSet<>(orderedSlugs.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(slug -> !slug.isBlank())
                .toList());
        String json = uniqueSlugs.stream()
                .map(this::quoteJsonString)
                .collect(Collectors.joining(",\n  ", "[\n  ", "\n]\n"));
        try {
            Files.writeString(directory.resolve(ORDER_FILE_NAME), json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write order file", exception);
        }
    }

    private List<String> insertSlug(List<String> existing, String slug, String beforeSlug) {
        List<String> updated = new ArrayList<>(existing);
        updated.remove(slug);
        if (beforeSlug != null && !beforeSlug.isBlank()) {
            int targetIndex = updated.indexOf(beforeSlug);
            if (targetIndex >= 0) {
                updated.add(targetIndex, slug);
                return updated;
            }
        }
        updated.add(slug);
        return updated;
    }

    private List<String> parseStringArray(String rawJson) {
        String trimmed = rawJson == null ? "" : rawJson.trim();
        if (trimmed.isBlank() || "[]".equals(trimmed)) {
            return List.of();
        }
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            throw new IllegalArgumentException("Invalid order file format");
        }

        String body = trimmed.substring(1, trimmed.length() - 1);
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean escaping = false;
        for (int index = 0; index < body.length(); index++) {
            char currentChar = body.charAt(index);
            if (!inString) {
                if (currentChar == '"') {
                    inString = true;
                    current.setLength(0);
                }
                continue;
            }
            if (escaping) {
                current.append(currentChar);
                escaping = false;
                continue;
            }
            if (currentChar == '\\') {
                escaping = true;
                continue;
            }
            if (currentChar == '"') {
                values.add(current.toString());
                inString = false;
                continue;
            }
            current.append(currentChar);
        }
        return values;
    }

    private String quoteJsonString(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private void writeMarkdown(Path markdownPath, String markdown) {
        try {
            Files.createDirectories(markdownPath.getParent());
            Files.writeString(markdownPath, markdown, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write markdown file " + markdownPath, exception);
        }
    }

    private String renderMarkdown(String title, String content) {
        String normalizedTitle = requireTitle(title);
        String normalizedContent = Optional.ofNullable(content).orElse("").strip();
        if (normalizedContent.isBlank()) {
            return "# " + normalizedTitle + "\n";
        }
        return "# " + normalizedTitle + "\n\n" + normalizedContent + "\n";
    }

    private String requireTitle(String title) {
        String normalizedTitle = Optional.ofNullable(title).orElse("").trim();
        if (normalizedTitle.isBlank()) {
            throw new IllegalArgumentException("Title cannot be empty");
        }
        return normalizedTitle;
    }

    private String deriveTitle(Path markdownPath) {
        if (INDEX_FILE_NAME.equals(markdownPath.getFileName().toString()) && markdownPath.getParent() != null) {
            return humanizeSlug(markdownPath.getParent().getFileName().toString());
        }
        String fileName = markdownPath.getFileName().toString();
        String slug = fileName.substring(0, fileName.length() - MARKDOWN_EXTENSION.length());
        return humanizeSlug(slug);
    }

    private String humanizeSlug(String slug) {
        return Arrays.stream(slug.split("-"))
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1))
                .collect(Collectors.joining(" "));
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

    private String normalizePath(String rawPath) {
        String normalized = Optional.ofNullable(rawPath).orElse("")
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

    private String joinPath(String parentPath, String slug) {
        if (parentPath == null || parentPath.isBlank()) {
            return slug;
        }
        return parentPath + "/" + slug;
    }

    private Instant readCreatedInstant(Path path) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            return attributes.creationTime().toInstant();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read file metadata", exception);
        }
    }

    private Instant readUpdatedInstant(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read file metadata", exception);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    private void copyRecursively(Path source, Path target) throws IOException {
        if (Files.isDirectory(source)) {
            Files.createDirectories(target);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
                for (Path child : stream) {
                    copyRecursively(child, target.resolve(child.getFileName().toString()));
                }
            }
            return;
        }
        Files.copy(source, target);
    }

    private void seedDemoContent(Path root) throws IOException {
        Path guidesDirectory = root.resolve("guides");
        if (!Files.exists(guidesDirectory)) {
            Files.createDirectories(guidesDirectory);
            writeMarkdown(guidesDirectory.resolve(INDEX_FILE_NAME),
                    renderMarkdown("Guides", "Start here when you need structured how-to documentation."));
            writeMarkdown(guidesDirectory.resolve("writing-notes.md"), renderMarkdown("Writing notes",
                    "Capture decisions, snippets, and operating procedures in markdown.\n\n## Tips\n\n- Keep sections focused\n- Use headings for fast navigation\n- Link related pages together"));
            writeMarkdown(guidesDirectory.resolve("team-onboarding.md"), renderMarkdown("Team onboarding",
                    "1. Read the overview\n2. Explore the tree\n3. Update pages directly in the editor"));
            saveOrderedSlugs(guidesDirectory, List.of("writing-notes", "team-onboarding"));
        }

        Path productDirectory = root.resolve("product");
        if (!Files.exists(productDirectory)) {
            Files.createDirectories(productDirectory);
            writeMarkdown(productDirectory.resolve(INDEX_FILE_NAME), renderMarkdown("Product",
                    "Use this section for specifications, roadmap notes, and release documentation."));
            writeMarkdown(productDirectory.resolve("roadmap.md"), renderMarkdown("Roadmap",
                    "## Next\n\n- Improve search relevance\n- Add asset uploads\n- Add export workflows"));
            saveOrderedSlugs(productDirectory, List.of("roadmap"));
        }

        saveOrderedSlugs(root, List.of("guides", "product"));
    }

    private String idForPath(String path) {
        return normalizePath(path).isBlank() ? "root" : normalizePath(path);
    }

    private Path resolveAssetPath(WikiNodeReference nodeReference, String assetName) {
        Path directPageAsset = getAssetsDirectory(nodeReference).resolve(assetName);
        if (Files.exists(directPageAsset)) {
            return directPageAsset;
        }
        if (nodeReference.getKind().isContainer()) {
            throw new WikiNotFoundException("Asset not found: " + assetName);
        }
        Path sectionAsset = nodeReference.getParentDirectory().resolve(".section-assets").resolve(assetName);
        if (Files.exists(sectionAsset)) {
            return sectionAsset;
        }
        throw new WikiNotFoundException("Asset not found: " + assetName);
    }

    private Path getAssetsDirectory(WikiNodeReference nodeReference) {
        Path containerDirectory = nodeReference.getKind().isContainer() ? nodeReference.getNodePath()
                : nodeReference.getParentDirectory();
        String slug = nodeReference.getKind().isContainer() ? ".section-assets" : ".assets-" + nodeReference.getSlug();
        return containerDirectory.resolve(slug);
    }

    private WikiAsset toAsset(Path assetPath, WikiNodeReference nodeReference) {
        return toAsset(assetPath, nodeReference, probeContentType(assetPath));
    }

    private WikiAsset toAsset(Path assetPath, WikiNodeReference nodeReference, String contentType) {
        try {
            return WikiAsset.builder()
                    .name(assetPath.getFileName().toString())
                    .path(assetPath(nodeReference.getPath(), assetPath.getFileName().toString()))
                    .size(Files.size(assetPath))
                    .contentType(contentType)
                    .build();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect asset", exception);
        }
    }

    private String assetPath(String pagePath, String assetName) {
        return assetPathPrefix(pagePath) + encodeUrlComponent(assetName);
    }

    private String assetPathPrefix(String pagePath) {
        return "/api/spaces/" + activeSpaceSlug() + "/assets?path=" + encodeUrlComponent(pagePath) + "&name=";
    }

    private String unencodedAssetPathPrefix(String pagePath) {
        return "/api/spaces/" + activeSpaceSlug() + "/assets?path=" + pagePath + "&name=";
    }

    private String legacyAssetPathPrefix(String pagePath) {
        return "/api/assets?path=" + pagePath + "&name=";
    }

    private String encodedLegacyAssetPathPrefix(String pagePath) {
        return "/api/assets?path=" + encodeUrlComponent(pagePath) + "&name=";
    }

    private String activeSpaceSlug() {
        String activeSpaceId = SpaceContextHolder.require();
        return spaceRepository.findById(activeSpaceId)
                .map(Space::getSlug)
                .map(this::encodeUrlComponent)
                .orElseGet(() -> encodeUrlComponent(activeSpaceId));
    }

    private String encodeUrlComponent(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String probeContentType(Path assetPath) {
        try {
            return Optional.ofNullable(Files.probeContentType(assetPath)).orElse("application/octet-stream");
        } catch (IOException exception) {
            return "application/octet-stream";
        }
    }

    private String sanitizeFileName(String fileName) {
        String sanitized = Optional.ofNullable(fileName).orElse("")
                .replace('\\', '/')
                .trim();
        if (sanitized.contains("/")) {
            sanitized = sanitized.substring(sanitized.lastIndexOf('/') + 1);
        }
        if (sanitized.isBlank() || sanitized.contains("..")) {
            throw new IllegalArgumentException("Invalid file name");
        }
        return sanitized;
    }

    private String ensureUniqueAssetName(Path assetsDirectory, String fileName) {
        String baseName = fileName;
        String extension = "";
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex > 0) {
            baseName = fileName.substring(0, extensionIndex);
            extension = fileName.substring(extensionIndex);
        }
        String candidate = fileName;
        while (Files.exists(assetsDirectory.resolve(candidate))) {
            candidate = baseName + "-" + UUID.randomUUID().toString().substring(0, 8) + extension;
        }
        return candidate;
    }

    private record HistoryMetadata(String recordedAt, String author, String reason, String summary) {
    }
}
