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

package me.golemcore.brain.adapter.out.filesystem.apikey;

import me.golemcore.brain.application.port.out.ApiKeyRepository;
import me.golemcore.brain.config.WikiProperties;
import me.golemcore.brain.domain.apikey.ApiKey;
import me.golemcore.brain.domain.auth.UserRole;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileApiKeyRepository implements ApiKeyRepository {

    private static final String FILE_NAME = "api-keys.jsonl";
    private static final String NULL_MARKER = "-";

    private final WikiProperties wikiProperties;

    @PostConstruct
    @Override
    public void initialize() {
        Path filePath = getFilePath();
        try {
            Files.createDirectories(filePath.getParent());
            if (!Files.exists(filePath)) {
                Files.writeString(filePath, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize api-key storage", exception);
        }
    }

    @Override
    public List<ApiKey> listAll() {
        try {
            Path filePath = getFilePath();
            if (!Files.exists(filePath)) {
                return List.of();
            }
            List<ApiKey> keys = new ArrayList<>();
            for (String line : Files.readAllLines(filePath, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                keys.add(parseLine(line));
            }
            return keys;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read api keys", exception);
        }
    }

    @Override
    public List<ApiKey> listBySpace(String spaceId) {
        return listAll().stream()
                .filter(key -> Objects.equals(key.getSpaceId(), spaceId))
                .toList();
    }

    @Override
    public Optional<ApiKey> findById(String id) {
        return listAll().stream().filter(key -> key.getId().equals(id)).findFirst();
    }

    @Override
    public ApiKey save(ApiKey apiKey) {
        List<ApiKey> keys = new ArrayList<>(listAll());
        keys.removeIf(existing -> existing.getId().equals(apiKey.getId()));
        keys.add(apiKey);
        writeKeys(keys);
        return apiKey;
    }

    @Override
    public void delete(String id) {
        List<ApiKey> keys = new ArrayList<>(listAll());
        keys.removeIf(key -> key.getId().equals(id));
        writeKeys(keys);
    }

    private void writeKeys(List<ApiKey> keys) {
        StringBuilder builder = new StringBuilder();
        for (ApiKey key : keys) {
            builder.append(formatLine(key)).append('\n');
        }
        try {
            Files.writeString(
                    getFilePath(),
                    builder.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write api keys", exception);
        }
    }

    private ApiKey parseLine(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 8) {
            throw new IllegalStateException("Invalid api key record: " + line);
        }
        Set<UserRole> roles = parts[4].isBlank()
                ? EnumSet.noneOf(UserRole.class)
                : Arrays.stream(parts[4].split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(UserRole::valueOf)
                        .collect(Collectors.toCollection(() -> EnumSet.noneOf(UserRole.class)));
        return ApiKey.builder()
                .id(parts[0])
                .name(parts[1])
                .subject(parts[2])
                .spaceId(NULL_MARKER.equals(parts[3]) ? null : parts[3])
                .roles(roles)
                .createdAt(Instant.parse(parts[5]))
                .expiresAt(NULL_MARKER.equals(parts[6]) ? null : Instant.parse(parts[6]))
                .revoked(Boolean.parseBoolean(parts[7]))
                .build();
    }

    private String formatLine(ApiKey key) {
        String rolesJoined = key.getRoles().stream().map(UserRole::name).collect(Collectors.joining(","));
        return String.join(
                "|",
                key.getId(),
                key.getName(),
                key.getSubject(),
                key.getSpaceId() == null ? NULL_MARKER : key.getSpaceId(),
                rolesJoined,
                key.getCreatedAt().toString(),
                key.getExpiresAt() == null ? NULL_MARKER : key.getExpiresAt().toString(),
                Boolean.toString(key.isRevoked()));
    }

    private Path getFilePath() {
        return wikiProperties.getStorageRoot().resolve(".auth").resolve(FILE_NAME);
    }
}
