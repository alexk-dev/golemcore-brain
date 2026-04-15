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

package me.golemcore.brain.adapter.out.filesystem.dynamicapi;

import me.golemcore.brain.config.WikiProperties;
import me.golemcore.brain.domain.llm.LlmToolCall;
import me.golemcore.brain.domain.llm.LlmToolResult;
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
