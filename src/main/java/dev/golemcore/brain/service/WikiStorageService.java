package dev.golemcore.brain.service;

import dev.golemcore.brain.config.WikiProperties;
import dev.golemcore.brain.domain.WikiNodeKind;
import dev.golemcore.brain.domain.WikiPage;
import dev.golemcore.brain.domain.WikiSearchHit;
import dev.golemcore.brain.domain.WikiTreeNode;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WikiStorageService {

    private static final String INDEX_FILE_NAME = "index.md";
    private static final String ORDER_FILE_NAME = ".order.json";
    private static final String MARKDOWN_EXTENSION = ".md";

    private final WikiProperties wikiProperties;

    @PostConstruct
    public void initializeStorage() {
        Path root = wikiProperties.getStorageRoot();
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
            throw new IllegalStateException("Failed to initialize wiki storage", exception);
        }
    }

    public WikiTreeNode getTree() {
        return toTreeNode(rootReference());
    }

    public WikiPage getPage(String rawPath) {
        NodeReference nodeReference = findReference(rawPath)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(rawPath)));
        return toWikiPage(nodeReference);
    }

    public WikiPage createPage(CreatePageRequest request) {
        NodeReference parentReference = requireSectionReference(request.getParentPath());
        String requestedSlug = Optional.ofNullable(request.getSlug()).orElse("");
        String slug = slugify(requestedSlug.isBlank() ? request.getTitle() : requestedSlug);
        WikiNodeKind kind = Optional.ofNullable(request.getKind())
                .orElseThrow(() -> new IllegalArgumentException("Node kind is required"));
        Path targetPath = buildNodePath(parentReference.getNodePath(), slug, kind);
        requireAvailable(targetPath, slug);

        try {
            if (kind == WikiNodeKind.SECTION) {
                Files.createDirectories(targetPath);
                writeMarkdown(targetPath.resolve(INDEX_FILE_NAME), renderMarkdown(request.getTitle(), request.getContent()));
            } else if (kind == WikiNodeKind.PAGE) {
                writeMarkdown(targetPath, renderMarkdown(request.getTitle(), request.getContent()));
            } else {
                throw new IllegalArgumentException("Unsupported node kind: " + kind);
            }
            saveOrderedSlugs(parentReference.getNodePath(), insertSlug(readOrderedSlugs(parentReference.getNodePath()), slug, null));
            return getPage(joinPath(parentReference.getPath(), slug));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create page", exception);
        }
    }

    public WikiPage updatePage(String rawPath, UpdatePageRequest request) {
        NodeReference nodeReference = findReference(rawPath)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(rawPath)));
        String normalizedTitle = requireTitle(request.getTitle());
        String requestedSlug = Optional.ofNullable(request.getSlug()).orElse("");
        String nextSlug = nodeReference.getKind() == WikiNodeKind.ROOT
                ? nodeReference.getSlug()
                : slugify(requestedSlug.isBlank() ? normalizedTitle : requestedSlug);
        String nextPath = nodeReference.getPath();

        try {
            if (nodeReference.getKind() != WikiNodeKind.ROOT && !nodeReference.getSlug().equals(nextSlug)) {
                nextPath = renameNode(nodeReference, nextSlug);
                nodeReference = findReference(nextPath)
                        .orElseThrow(() -> new IllegalStateException("Page missing after rename"));
            }
            writeMarkdown(nodeReference.getMarkdownPath(), renderMarkdown(normalizedTitle, request.getContent()));
            return getPage(nextPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to update page", exception);
        }
    }

    public void deletePage(String rawPath) {
        NodeReference nodeReference = findReference(rawPath)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(rawPath)));
        if (nodeReference.getKind() == WikiNodeKind.ROOT) {
            throw new IllegalArgumentException("Root page cannot be deleted");
        }
        try {
            deleteRecursively(nodeReference.getNodePath());
            List<String> updatedOrder = new ArrayList<>(readOrderedSlugs(nodeReference.getParentDirectory()));
            updatedOrder.remove(nodeReference.getSlug());
            saveOrderedSlugs(nodeReference.getParentDirectory(), updatedOrder);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete page", exception);
        }
    }

    public WikiPage movePage(String rawPath, MovePageRequest request) {
        NodeReference sourceReference = findReference(rawPath)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(rawPath)));
        if (sourceReference.getKind() == WikiNodeKind.ROOT) {
            throw new IllegalArgumentException("Root page cannot be moved");
        }

        NodeReference targetParentReference = requireSectionReference(request.getTargetParentPath());
        String requestedSlug = Optional.ofNullable(request.getTargetSlug()).orElse("");
        String targetSlug = slugify(requestedSlug.isBlank() ? sourceReference.getSlug() : requestedSlug);
        Path targetNodePath = buildNodePath(targetParentReference.getNodePath(), targetSlug, sourceReference.getKind());
        boolean sameNode = sourceReference.getNodePath().equals(targetNodePath);
        if (sourceReference.getKind() == WikiNodeKind.SECTION && targetParentReference.getNodePath().startsWith(sourceReference.getNodePath())) {
            throw new IllegalArgumentException("A section cannot be moved into itself");
        }
        if (!sameNode) {
            requireAvailable(targetNodePath, targetSlug);
        }

        try {
            if (!sameNode) {
                Files.move(sourceReference.getNodePath(), targetNodePath, StandardCopyOption.REPLACE_EXISTING);
            }
            List<String> sourceOrder = new ArrayList<>(readOrderedSlugs(sourceReference.getParentDirectory()));
            sourceOrder.remove(sourceReference.getSlug());
            saveOrderedSlugs(sourceReference.getParentDirectory(), sourceOrder);

            List<String> targetOrder = new ArrayList<>(readOrderedSlugs(targetParentReference.getNodePath()));
            if (sameNode) {
                targetOrder.remove(sourceReference.getSlug());
            }
            saveOrderedSlugs(targetParentReference.getNodePath(), insertSlug(targetOrder, targetSlug, request.getBeforeSlug()));
            return getPage(joinPath(targetParentReference.getPath(), targetSlug));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to move page", exception);
        }
    }

    public WikiPage copyPage(String rawPath, CopyPageRequest request) {
        NodeReference sourceReference = findReference(rawPath)
                .orElseThrow(() -> new WikiNotFoundException("Page not found: " + normalizePath(rawPath)));
        NodeReference targetParentReference = requireSectionReference(request.getTargetParentPath());
        String requestedSlug = Optional.ofNullable(request.getTargetSlug()).orElse("");
        String targetSlug = slugify(requestedSlug.isBlank() ? sourceReference.getSlug() + "-copy" : requestedSlug);
        Path targetNodePath = buildNodePath(targetParentReference.getNodePath(), targetSlug, sourceReference.getKind());
        requireAvailable(targetNodePath, targetSlug);

        try {
            copyRecursively(sourceReference.getNodePath(), targetNodePath);
            saveOrderedSlugs(targetParentReference.getNodePath(), insertSlug(readOrderedSlugs(targetParentReference.getNodePath()), targetSlug, request.getBeforeSlug()));
            return getPage(joinPath(targetParentReference.getPath(), targetSlug));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to copy page", exception);
        }
    }

    public void sortChildren(String rawPath, SortChildrenRequest request) {
        NodeReference sectionReference = requireSectionReference(rawPath);
        List<NodeReference> children = listChildren(sectionReference.getNodePath());
        Set<String> existingSlugs = children.stream().map(NodeReference::getSlug).collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> requestedOrder = Optional.ofNullable(request.getOrderedSlugs()).orElse(List.of()).stream()
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

    public List<WikiSearchHit> search(String query) {
        String normalizedQuery = Optional.ofNullable(query).orElse("").trim().toLowerCase(Locale.ROOT);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }
        return flattenReferences(rootReference()).stream()
                .map(this::toSearchDocument)
                .filter(document -> document.matches(normalizedQuery))
                .sorted(Comparator.comparing(SearchDocument::getTitle, String.CASE_INSENSITIVE_ORDER))
                .limit(50)
                .map(document -> WikiSearchHit.builder()
                        .path(document.getPath())
                        .title(document.getTitle())
                        .excerpt(document.buildExcerpt(normalizedQuery))
                        .parentPath(document.getParentPath())
                        .kind(document.getKind())
                        .build())
                .toList();
    }

    private void seedDemoContent(Path root) throws IOException {
        Path guidesDirectory = root.resolve("guides");
        if (!Files.exists(guidesDirectory)) {
            Files.createDirectories(guidesDirectory);
            writeMarkdown(guidesDirectory.resolve(INDEX_FILE_NAME), renderMarkdown("Guides", "Start here when you need structured how-to documentation."));
            writeMarkdown(guidesDirectory.resolve("writing-notes.md"), renderMarkdown("Writing notes", "Capture decisions, snippets, and operating procedures in markdown.\n\n## Tips\n\n- Keep sections focused\n- Use headings for fast navigation\n- Link related pages together"));
            writeMarkdown(guidesDirectory.resolve("team-onboarding.md"), renderMarkdown("Team onboarding", "1. Read the overview\n2. Explore the tree\n3. Update pages directly in the editor"));
            saveOrderedSlugs(guidesDirectory, List.of("writing-notes", "team-onboarding"));
        }

        Path productDirectory = root.resolve("product");
        if (!Files.exists(productDirectory)) {
            Files.createDirectories(productDirectory);
            writeMarkdown(productDirectory.resolve(INDEX_FILE_NAME), renderMarkdown("Product", "Use this section for specifications, roadmap notes, and release documentation."));
            writeMarkdown(productDirectory.resolve("roadmap.md"), renderMarkdown("Roadmap", "## Next\n\n- Improve search relevance\n- Add asset uploads\n- Add export workflows"));
            saveOrderedSlugs(productDirectory, List.of("roadmap"));
        }

        saveOrderedSlugs(root, List.of("guides", "product"));
    }

    private NodeReference requireSectionReference(String rawPath) {
        NodeReference nodeReference = findReference(rawPath)
                .orElseThrow(() -> new WikiNotFoundException("Section not found: " + normalizePath(rawPath)));
        if (nodeReference.getKind() == WikiNodeKind.PAGE) {
            throw new IllegalArgumentException("Target path is not a section");
        }
        return nodeReference;
    }

    private Optional<NodeReference> findReference(String rawPath) {
        String normalizedPath = normalizePath(rawPath);
        if (normalizedPath.isBlank()) {
            return Optional.of(rootReference());
        }

        Path sectionPath = wikiProperties.getStorageRoot().resolve(normalizedPath);
        if (Files.isDirectory(sectionPath) && Files.exists(sectionPath.resolve(INDEX_FILE_NAME))) {
            return Optional.of(buildSectionReference(sectionPath));
        }

        Path pagePath = wikiProperties.getStorageRoot().resolve(normalizedPath + MARKDOWN_EXTENSION);
        if (Files.isRegularFile(pagePath)) {
            return Optional.of(buildPageReference(pagePath));
        }
        return Optional.empty();
    }

    private NodeReference rootReference() {
        return NodeReference.builder()
                .path("")
                .parentPath(null)
                .slug("")
                .kind(WikiNodeKind.ROOT)
                .nodePath(wikiProperties.getStorageRoot())
                .parentDirectory(null)
                .markdownPath(wikiProperties.getStorageRoot().resolve(INDEX_FILE_NAME))
                .build();
    }

    private NodeReference buildSectionReference(Path sectionPath) {
        Path relativePath = wikiProperties.getStorageRoot().relativize(sectionPath);
        String path = normalizePath(relativePath.toString());
        String parentPath = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "";
        return NodeReference.builder()
                .path(path)
                .parentPath(parentPath)
                .slug(sectionPath.getFileName().toString())
                .kind(WikiNodeKind.SECTION)
                .nodePath(sectionPath)
                .parentDirectory(sectionPath.getParent())
                .markdownPath(sectionPath.resolve(INDEX_FILE_NAME))
                .build();
    }

    private NodeReference buildPageReference(Path pagePath) {
        Path relativePath = wikiProperties.getStorageRoot().relativize(pagePath);
        String slug = pagePath.getFileName().toString().replace(MARKDOWN_EXTENSION, "");
        Path parentPath = relativePath.getParent();
        String normalizedParentPath = parentPath == null ? "" : normalizePath(parentPath.toString());
        return NodeReference.builder()
                .path(joinPath(normalizedParentPath, slug))
                .parentPath(normalizedParentPath)
                .slug(slug)
                .kind(WikiNodeKind.PAGE)
                .nodePath(pagePath)
                .parentDirectory(pagePath.getParent())
                .markdownPath(pagePath)
                .build();
    }

    private WikiTreeNode toTreeNode(NodeReference nodeReference) {
        PageDocument pageDocument = readDocument(nodeReference.getMarkdownPath());
        List<WikiTreeNode> children = nodeReference.getKind() == WikiNodeKind.PAGE
                ? List.of()
                : listChildren(nodeReference.getNodePath()).stream().map(this::toTreeNode).toList();
        return WikiTreeNode.builder()
                .path(nodeReference.getPath())
                .parentPath(nodeReference.getParentPath())
                .title(pageDocument.getTitle())
                .slug(nodeReference.getSlug())
                .kind(nodeReference.getKind())
                .hasChildren(!children.isEmpty())
                .children(children)
                .build();
    }

    private WikiPage toWikiPage(NodeReference nodeReference) {
        PageDocument pageDocument = readDocument(nodeReference.getMarkdownPath());
        List<WikiTreeNode> children = nodeReference.getKind() == WikiNodeKind.PAGE
                ? List.of()
                : listChildren(nodeReference.getNodePath()).stream().map(this::toTreeNode).toList();
        return WikiPage.builder()
                .path(nodeReference.getPath())
                .parentPath(nodeReference.getParentPath())
                .title(pageDocument.getTitle())
                .slug(nodeReference.getSlug())
                .kind(nodeReference.getKind())
                .content(pageDocument.getBody())
                .createdAt(formatInstant(readCreatedInstant(nodeReference.getMarkdownPath())))
                .updatedAt(formatInstant(readUpdatedInstant(nodeReference.getMarkdownPath())))
                .children(children)
                .build();
    }

    private List<NodeReference> listChildren(Path directory) {
        List<NodeReference> references = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path candidate : stream) {
                if (Files.isDirectory(candidate) && Files.exists(candidate.resolve(INDEX_FILE_NAME))) {
                    references.add(buildSectionReference(candidate));
                }
                if (Files.isRegularFile(candidate)
                        && candidate.getFileName().toString().endsWith(MARKDOWN_EXTENSION)
                        && !candidate.getFileName().toString().equals(INDEX_FILE_NAME)) {
                    references.add(buildPageReference(candidate));
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read child pages", exception);
        }

        references.sort(Comparator.comparing(NodeReference::getSlug, String.CASE_INSENSITIVE_ORDER));
        List<String> orderedSlugs = readOrderedSlugs(directory);
        List<NodeReference> orderedReferences = new ArrayList<>();
        for (String orderedSlug : orderedSlugs) {
            for (NodeReference reference : references) {
                if (reference.getSlug().equals(orderedSlug) && !orderedReferences.contains(reference)) {
                    orderedReferences.add(reference);
                }
            }
        }
        for (NodeReference reference : references) {
            if (!orderedReferences.contains(reference)) {
                orderedReferences.add(reference);
            }
        }
        return orderedReferences;
    }

    private List<NodeReference> flattenReferences(NodeReference rootReference) {
        List<NodeReference> references = new ArrayList<>();
        references.add(rootReference);
        if (rootReference.getKind() != WikiNodeKind.PAGE) {
            for (NodeReference childReference : listChildren(rootReference.getNodePath())) {
                references.addAll(flattenReferences(childReference));
            }
        }
        return references;
    }

    private SearchDocument toSearchDocument(NodeReference nodeReference) {
        PageDocument pageDocument = readDocument(nodeReference.getMarkdownPath());
        return SearchDocument.builder()
                .path(nodeReference.getPath())
                .parentPath(nodeReference.getParentPath())
                .title(pageDocument.getTitle())
                .body(pageDocument.getBody())
                .kind(nodeReference.getKind())
                .build();
    }

    private String renameNode(NodeReference nodeReference, String nextSlug) throws IOException {
        Path targetPath = buildNodePath(nodeReference.getParentDirectory(), nextSlug, nodeReference.getKind());
        requireAvailable(targetPath, nextSlug);
        Files.move(nodeReference.getNodePath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        List<String> updatedOrder = new ArrayList<>(readOrderedSlugs(nodeReference.getParentDirectory()));
        int index = updatedOrder.indexOf(nodeReference.getSlug());
        if (index >= 0) {
            updatedOrder.set(index, nextSlug);
        }
        saveOrderedSlugs(nodeReference.getParentDirectory(), updatedOrder);
        return joinPath(nodeReference.getParentPath(), nextSlug);
    }

    private void requireAvailable(Path targetPath, String slug) {
        if (Files.exists(targetPath)) {
            throw new IllegalArgumentException("A page with this slug already exists: " + slug);
        }
    }

    private Path buildNodePath(Path parentDirectory, String slug, WikiNodeKind kind) {
        if (kind == WikiNodeKind.SECTION || kind == WikiNodeKind.ROOT) {
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
            Files.writeString(directory.resolve(ORDER_FILE_NAME), json, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
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
        if (trimmed.isBlank() || trimmed.equals("[]")) {
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

    private PageDocument readDocument(Path markdownPath) {
        try {
            String rawContent = Files.readString(markdownPath, StandardCharsets.UTF_8);
            List<String> lines = Arrays.asList(rawContent.split("\\R", -1));
            if (lines.isEmpty()) {
                return new PageDocument("Untitled", "");
            }
            String firstLine = lines.getFirst();
            if (firstLine.startsWith("# ")) {
                String title = firstLine.substring(2).trim();
                String body = lines.stream().skip(2).collect(Collectors.joining("\n")).strip();
                return new PageDocument(title.isBlank() ? deriveTitle(markdownPath) : title, body);
            }
            return new PageDocument(deriveTitle(markdownPath), rawContent.strip());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read markdown file " + markdownPath, exception);
        }
    }

    private void writeMarkdown(Path markdownPath, String markdown) {
        try {
            Files.createDirectories(markdownPath.getParent());
            Files.writeString(markdownPath, markdown, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
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
        if (markdownPath.getFileName().toString().equals(INDEX_FILE_NAME) && markdownPath.getParent() != null) {
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
        if (normalized.equals(".")) {
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

    private String formatInstant(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant);
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

    @Value
    private static class PageDocument {
        String title;
        String body;
    }

    @Value
    @Builder
    private static class NodeReference {
        String path;
        String parentPath;
        String slug;
        WikiNodeKind kind;
        Path nodePath;
        Path parentDirectory;
        Path markdownPath;
    }

    @Value
    @Builder
    private static class SearchDocument {
        String path;
        String parentPath;
        String title;
        String body;
        WikiNodeKind kind;

        private boolean matches(String query) {
            String normalizedText = (title + "\n" + body).toLowerCase(Locale.ROOT);
            return normalizedText.contains(query);
        }

        private String buildExcerpt(String query) {
            if (body == null || body.isBlank()) {
                return "No additional content";
            }
            String compact = body.replaceAll("\\s+", " ").trim();
            String normalizedBody = compact.toLowerCase(Locale.ROOT);
            int index = normalizedBody.indexOf(query);
            if (index < 0) {
                return compact.length() > 180 ? compact.substring(0, 177) + "..." : compact;
            }
            int start = Math.max(0, index - 60);
            int end = Math.min(compact.length(), index + query.length() + 110);
            String excerpt = compact.substring(start, end);
            if (start > 0) {
                excerpt = "..." + excerpt;
            }
            if (end < compact.length()) {
                excerpt = excerpt + "...";
            }
            return excerpt;
        }
    }

    @Value
    @Builder
    public static class CreatePageRequest {
        String parentPath;
        String title;
        String slug;
        String content;
        WikiNodeKind kind;
    }

    @Value
    @Builder
    public static class UpdatePageRequest {
        String title;
        String slug;
        String content;
    }

    @Value
    @Builder
    public static class MovePageRequest {
        String targetParentPath;
        String targetSlug;
        String beforeSlug;
    }

    @Value
    @Builder
    public static class CopyPageRequest {
        String targetParentPath;
        String targetSlug;
        String beforeSlug;
    }

    @Value
    @Builder
    public static class SortChildrenRequest {
        List<String> orderedSlugs;
    }
}
