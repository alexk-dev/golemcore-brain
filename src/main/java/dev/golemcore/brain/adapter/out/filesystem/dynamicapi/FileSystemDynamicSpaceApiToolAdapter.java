package dev.golemcore.brain.adapter.out.filesystem.dynamicapi;

import dev.golemcore.brain.application.port.out.DynamicSpaceApiToolPort;
import dev.golemcore.brain.config.WikiProperties;
import dev.golemcore.brain.domain.llm.LlmToolCall;
import dev.golemcore.brain.domain.llm.LlmToolDefinition;
import dev.golemcore.brain.domain.llm.LlmToolResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileSystemDynamicSpaceApiToolAdapter implements DynamicSpaceApiToolPort {

    private static final String SPACES_DIRECTORY = "spaces";
    private static final String MARKDOWN_EXTENSION = ".md";
    private static final String INDEX_FILE = "index.md";
    private static final long MAX_READ_BYTES = 250_000;
    private static final int MAX_LIST_ENTRIES = 100;
    private static final int DEFAULT_SEARCH_LIMIT = 10;
    private static final int MAX_SEARCH_LIMIT = 25;
    private static final int EXCERPT_RADIUS = 160;

    private final WikiProperties wikiProperties;

    @Override
    public List<LlmToolDefinition> definitions() {
        return List.of(
                searchFilesDefinition(),
                readFileDefinition(),
                listDirectoryDefinition());
    }

    @Override
    public LlmToolResult execute(String spaceId, LlmToolCall toolCall) {
        if (toolCall == null || toolCall.getName() == null) {
            return LlmToolResult.failure("Tool call name is required");
        }
        Map<String, Object> arguments = toolCall.getArguments() != null ? toolCall.getArguments() : Map.of();
        return switch (toolCall.getName()) {
        case "search_files" -> searchFiles(spaceId, arguments);
        case "read_file" -> readFile(spaceId, arguments);
        case "list_directory" -> listDirectory(spaceId, arguments);
        default -> LlmToolResult.failure("Unknown tool: " + toolCall.getName());
        };
    }

    private LlmToolDefinition searchFilesDefinition() {
        return LlmToolDefinition.builder()
                .name("search_files")
                .description("Search markdown files in the active space by path, title, and content.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of(
                                        "type", "string",
                                        "description",
                                        "Case-insensitive search query. Supports * and ? wildcard masks."),
                                "maxResults", Map.of(
                                        "type", "integer",
                                        "description", "Maximum result count, 1-25")),
                        "required", List.of("query")))
                .build();
    }

    private LlmToolDefinition readFileDefinition() {
        return LlmToolDefinition.builder()
                .name("read_file")
                .description("Read a UTF-8 text file from the active space. Paths are relative to the space root.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of(
                                        "type", "string",
                                        "description", "File path relative to the space root, for example index.md")),
                        "required", List.of("path")))
                .build();
    }

    private LlmToolDefinition listDirectoryDefinition() {
        return LlmToolDefinition.builder()
                .name("list_directory")
                .description("List files and directories in the active space. Paths are relative to the space root.")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of(
                                        "type", "string",
                                        "description",
                                        "Directory path relative to the space root. Use empty string for root.")),
                        "required", List.of()))
                .build();
    }

    private LlmToolResult searchFiles(String spaceId, Map<String, Object> arguments) {
        String query = stringArgument(arguments, "query", null);
        if (query == null || query.isBlank()) {
            return LlmToolResult.failure("query is required");
        }
        int maxResults = Math.min(MAX_SEARCH_LIMIT, Math.max(1, intArgument(arguments, "maxResults",
                DEFAULT_SEARCH_LIMIT)));
        MaskMatcher matcher = new MaskMatcher(query);
        Path root = spaceRoot(spaceId);
        List<Map<String, Object>> matches = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(MARKDOWN_EXTENSION))
                    .filter(path -> !hasHiddenSegment(root.relativize(path)))
                    .sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            for (Path path : files) {
                if (matches.size() >= maxResults) {
                    break;
                }
                String content = readSmallFile(path);
                String relativePath = normalizeSeparators(root.relativize(path).toString());
                String title = extractTitle(content, relativePath);
                String haystack = relativePath + "\n" + title + "\n" + content;
                if (matcher.matches(haystack)) {
                    matches.add(Map.of(
                            "path", relativePath,
                            "wikiPath", toWikiPath(relativePath),
                            "title", title,
                            "excerpt", excerpt(content, query)));
                }
            }
            return LlmToolResult.success("Found " + matches.size() + " matching markdown files",
                    Map.of("query", query, "matches", matches));
        } catch (IOException exception) {
            return LlmToolResult.failure("Search failed: " + exception.getMessage());
        }
    }

    private LlmToolResult readFile(String spaceId, Map<String, Object> arguments) {
        String pathValue = stringArgument(arguments, "path", null);
        if (pathValue == null || pathValue.isBlank()) {
            return LlmToolResult.failure("path is required");
        }
        Path root = spaceRoot(spaceId);
        Path path = resolveSafePath(root, pathValue);
        if (path == null) {
            return LlmToolResult.failure("Invalid path: must stay within the space root");
        }
        if (!Files.exists(path)) {
            return LlmToolResult.failure("File not found: " + pathValue);
        }
        if (!Files.isRegularFile(path)) {
            return LlmToolResult.failure("Not a file: " + pathValue);
        }
        if (hasHiddenSegment(root.relativize(path))) {
            return LlmToolResult.failure("Hidden files are not available to this tool");
        }
        try {
            long size = Files.size(path);
            if (size > MAX_READ_BYTES) {
                return LlmToolResult.failure("File is too large to read through the tool");
            }
            String content = Files.readString(path, StandardCharsets.UTF_8);
            String relativePath = normalizeSeparators(root.relativize(path).toString());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", relativePath);
            data.put("wikiPath", toWikiPath(relativePath));
            data.put("size", size);
            data.put("content", content);
            return LlmToolResult.success(content, data);
        } catch (IOException exception) {
            return LlmToolResult.failure("Failed to read file: " + exception.getMessage());
        }
    }

    private LlmToolResult listDirectory(String spaceId, Map<String, Object> arguments) {
        String pathValue = stringArgument(arguments, "path", "");
        Path root = spaceRoot(spaceId);
        Path path = resolveSafePath(root, pathValue == null ? "" : pathValue);
        if (path == null) {
            return LlmToolResult.failure("Invalid path: must stay within the space root");
        }
        if (!Files.exists(path)) {
            return LlmToolResult.failure("Directory not found: " + pathValue);
        }
        if (!Files.isDirectory(path)) {
            return LlmToolResult.failure("Not a directory: " + pathValue);
        }
        if (!root.equals(path) && hasHiddenSegment(root.relativize(path))) {
            return LlmToolResult.failure("Hidden directories are not available to this tool");
        }
        try (Stream<Path> stream = Files.list(path)) {
            List<Map<String, Object>> entries = stream
                    .filter(candidate -> !hasHiddenSegment(root.relativize(candidate)))
                    .sorted(Comparator.comparing(candidate -> candidate.getFileName().toString(),
                            String.CASE_INSENSITIVE_ORDER))
                    .limit(MAX_LIST_ENTRIES)
                    .map(candidate -> directoryEntry(root, candidate))
                    .toList();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", normalizeSeparators(root.relativize(path).toString()));
            data.put("entries", entries);
            return LlmToolResult.success("Listed " + entries.size() + " entries", data);
        } catch (IOException exception) {
            return LlmToolResult.failure("Failed to list directory: " + exception.getMessage());
        }
    }

    private static class MaskMatcher {
        private final String query;
        private final Pattern pattern;

        MaskMatcher(String query) {
            this.query = query.trim().toLowerCase(Locale.ROOT);
            this.pattern = buildPattern(this.query);
        }

        boolean matches(String value) {
            String normalizedValue = value.toLowerCase(Locale.ROOT);
            return hasWildcard(query)
                    ? pattern.matcher(normalizedValue).find()
                    : normalizedValue.contains(query);
        }

        private static Pattern buildPattern(String query) {
            if (hasWildcard(query)) {
                return Pattern.compile(globToRegex(query));
            }
            return Pattern.compile(Pattern.quote(query));
        }

        private static boolean hasWildcard(String query) {
            return query.indexOf('*') >= 0 || query.indexOf('?') >= 0;
        }

        private static String globToRegex(String query) {
            StringBuilder regex = new StringBuilder();
            for (int index = 0; index < query.length(); index++) {
                char character = query.charAt(index);
                if (character == '*') {
                    regex.append(".*");
                } else if (character == '?') {
                    regex.append('.');
                } else {
                    appendEscapedRegexCharacter(regex, character);
                }
            }
            return regex.toString();
        }

        private static void appendEscapedRegexCharacter(StringBuilder regex, char character) {
            if ("\\.[]{}()+-^$|".indexOf(character) >= 0) {
                regex.append('\\');
            }
            regex.append(character);
        }
    }

    private Map<String, Object> directoryEntry(Path root, Path candidate) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", candidate.getFileName().toString());
        entry.put("path", normalizeSeparators(root.relativize(candidate).toString()));
        entry.put("type", Files.isDirectory(candidate) ? "directory" : "file");
        try {
            BasicFileAttributes attributes = Files.readAttributes(candidate, BasicFileAttributes.class);
            entry.put("size", attributes.size());
            entry.put("modified", attributes.lastModifiedTime().toString());
        } catch (IOException ignored) {
            entry.put("size", null);
        }
        return entry;
    }

    private Path resolveSafePath(Path root, String pathValue) {
        try {
            Path relativePath = Path.of(pathValue);
            if (relativePath.isAbsolute()) {
                return null;
            }
            Path resolved = root.resolve(relativePath).normalize();
            if (!resolved.startsWith(root)) {
                return null;
            }
            if (Files.exists(resolved)) {
                Path realRoot = root.toRealPath();
                Path realResolved = resolved.toRealPath();
                if (!realResolved.startsWith(realRoot)) {
                    return null;
                }
            }
            return resolved;
        } catch (InvalidPathException | IOException exception) {
            return null;
        }
    }

    private Path spaceRoot(String spaceId) {
        return wikiProperties.getStorageRoot().resolve(SPACES_DIRECTORY).resolve(spaceId);
    }

    private boolean hasHiddenSegment(Path relativePath) {
        for (Path segment : relativePath) {
            String value = segment.toString();
            if (value.startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private String readSmallFile(Path path) throws IOException {
        if (Files.size(path) > MAX_READ_BYTES) {
            return "";
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private String extractTitle(String content, String fallbackPath) {
        return content.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("# "))
                .map(line -> line.substring(2).trim())
                .filter(title -> !title.isBlank())
                .findFirst()
                .orElse(fallbackPath);
    }

    private String excerpt(String content, String query) {
        String lowerContent = content.toLowerCase(Locale.ROOT);
        int index = lowerContent.indexOf(query.toLowerCase(Locale.ROOT));
        if (index < 0) {
            return content.length() <= EXCERPT_RADIUS * 2
                    ? content
                    : content.substring(0, EXCERPT_RADIUS * 2);
        }
        int start = Math.max(0, index - EXCERPT_RADIUS);
        int end = Math.min(content.length(), index + query.length() + EXCERPT_RADIUS);
        return content.substring(start, end).replaceAll("\\s+", " ").trim();
    }

    private String toWikiPath(String relativePath) {
        String normalized = normalizeSeparators(relativePath);
        if (INDEX_FILE.equals(normalized)) {
            return "";
        }
        if (normalized.endsWith("/" + INDEX_FILE)) {
            return normalized.substring(0, normalized.length() - INDEX_FILE.length() - 1);
        }
        if (normalized.endsWith(MARKDOWN_EXTENSION)) {
            return normalized.substring(0, normalized.length() - MARKDOWN_EXTENSION.length());
        }
        return normalized;
    }

    private String normalizeSeparators(String path) {
        return path.replace('\\', '/');
    }

    private String stringArgument(Map<String, Object> arguments, String name, String fallback) {
        Object value = arguments.get(name);
        return value instanceof String text ? text : fallback;
    }

    private int intArgument(Map<String, Object> arguments, String name, int fallback) {
        Object value = arguments.get(name);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
