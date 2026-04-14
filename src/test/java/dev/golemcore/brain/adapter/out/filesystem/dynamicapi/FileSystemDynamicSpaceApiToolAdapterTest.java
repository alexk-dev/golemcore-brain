package dev.golemcore.brain.adapter.out.filesystem.dynamicapi;

import dev.golemcore.brain.config.WikiProperties;
import dev.golemcore.brain.domain.llm.LlmToolCall;
import dev.golemcore.brain.domain.llm.LlmToolResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemDynamicSpaceApiToolAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldSearchFilesByWildcardMask() throws IOException {
        WikiProperties properties = new WikiProperties();
        properties.setStorageRoot(tempDir);
        Path spaceRoot = tempDir.resolve("spaces").resolve("space-1");
        Files.createDirectories(spaceRoot.resolve("docs"));
        Files.writeString(spaceRoot.resolve("solar-system-known-bodies.md"),
                "# Known bodies\nMercury Venus Earth");
        Files.writeString(spaceRoot.resolve("docs").resolve("mission.md"),
                "# Voyager\nInterstellar probe notes");
        Files.writeString(spaceRoot.resolve("docs").resolve("operations.md"),
                "# Operations\nDeployment checklist");
        FileSystemDynamicSpaceApiToolAdapter adapter = new FileSystemDynamicSpaceApiToolAdapter(properties);

        LlmToolResult result = adapter.execute("space-1", LlmToolCall.builder()
                .name("search_files")
                .arguments(Map.of("query", "solar*bod*"))
                .build());

        assertTrue(result.isSuccess());
        assertEquals(List.of("solar-system-known-bodies.md"), resultPaths(result));
    }

    @SuppressWarnings("unchecked")
    private List<String> resultPaths(LlmToolResult result) {
        Map<String, Object> data = (Map<String, Object>) result.getData();
        List<Map<String, Object>> matches = (List<Map<String, Object>>) data.get("matches");
        return matches.stream()
                .map(match -> (String) match.get("path"))
                .toList();
    }
}
