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

import me.golemcore.brain.application.port.out.DynamicSpaceApiRepository;
import me.golemcore.brain.config.WikiProperties;
import me.golemcore.brain.domain.dynamicapi.DynamicSpaceApiSettings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class FileDynamicSpaceApiRepository implements DynamicSpaceApiRepository {

    private static final String SPACES_DIRECTORY = "spaces";
    private static final String SETTINGS_FILE = ".dynamic-apis.json";

    private final WikiProperties wikiProperties;
    private final ObjectMapper objectMapper;

    @Override
    public synchronized DynamicSpaceApiSettings load(String spaceId) {
        Path filePath = filePath(spaceId);
        if (!Files.exists(filePath)) {
            return DynamicSpaceApiSettings.builder().build();
        }
        try {
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return DynamicSpaceApiSettings.builder().build();
            }
            DynamicSpaceApiSettings settings = objectMapper.readValue(content, DynamicSpaceApiSettings.class);
            return settings != null ? settings : DynamicSpaceApiSettings.builder().build();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read dynamic space API settings", exception);
        }
    }

    @Override
    public synchronized DynamicSpaceApiSettings save(String spaceId, DynamicSpaceApiSettings settings) {
        DynamicSpaceApiSettings safeSettings = settings != null ? settings : DynamicSpaceApiSettings.builder().build();
        Path filePath = filePath(spaceId);
        try {
            Files.createDirectories(filePath.getParent());
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(safeSettings);
            Files.writeString(
                    filePath,
                    json + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            return safeSettings;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write dynamic space API settings", exception);
        }
    }

    private Path filePath(String spaceId) {
        return wikiProperties.getStorageRoot().resolve(SPACES_DIRECTORY).resolve(spaceId).resolve(SETTINGS_FILE);
    }
}
