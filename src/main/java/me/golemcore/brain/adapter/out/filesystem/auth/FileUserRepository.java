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

package me.golemcore.brain.adapter.out.filesystem.auth;

import me.golemcore.brain.application.port.out.auth.UserRepository;
import me.golemcore.brain.config.WikiProperties;
import me.golemcore.brain.domain.auth.SpaceMembership;
import me.golemcore.brain.domain.auth.UserRole;
import me.golemcore.brain.domain.auth.WikiUser;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileUserRepository implements UserRepository {

    private static final String USERS_FILE_NAME = "users.jsonl";
    private static final String NO_MEMBERSHIPS = "-";

    private final WikiProperties wikiProperties;

    @PostConstruct
    @Override
    public void initialize() {
        Path filePath = getUsersFilePath();
        try {
            Files.createDirectories(filePath.getParent());
            if (!Files.exists(filePath)) {
                Files.writeString(filePath, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize users storage", exception);
        }
    }

    @Override
    public List<WikiUser> listUsers() {
        try {
            if (!Files.exists(getUsersFilePath())) {
                return List.of();
            }
            List<WikiUser> users = new ArrayList<>();
            for (String line : Files.readAllLines(getUsersFilePath(), StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                users.add(parseLine(line));
            }
            return users;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read users", exception);
        }
    }

    @Override
    public Optional<WikiUser> findById(String userId) {
        return listUsers().stream().filter(user -> user.getId().equals(userId)).findFirst();
    }

    @Override
    public Optional<WikiUser> findByUsernameOrEmail(String identifier) {
        String normalized = identifier.toLowerCase(Locale.ROOT);
        return listUsers().stream()
                .filter(user -> user.getUsername().toLowerCase(Locale.ROOT).equals(normalized)
                        || user.getEmail().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }

    @Override
    public WikiUser save(WikiUser user) {
        List<WikiUser> users = new ArrayList<>(listUsers());
        users.removeIf(existing -> existing.getId().equals(user.getId()));
        users.add(user);
        writeUsers(users);
        return user;
    }

    @Override
    public void delete(String userId) {
        List<WikiUser> users = new ArrayList<>(listUsers());
        users.removeIf(user -> user.getId().equals(userId));
        writeUsers(users);
    }

    @Override
    public WikiUser createAdminIfMissing(String username, String email, String passwordHash) {
        Optional<WikiUser> existingUser = findByUsernameOrEmail(username);
        if (existingUser.isPresent()) {
            WikiUser user = existingUser.get();
            if (!user.isGlobalAdmin()) {
                WikiUser updated = user.toBuilder()
                        .clearMemberships()
                        .membership(SpaceMembership.builder().spaceId(null).role(UserRole.ADMIN).build())
                        .role(UserRole.ADMIN)
                        .build();
                save(updated);
                return updated;
            }
            return user;
        }
        WikiUser admin = WikiUser.builder()
                .id(UUID.randomUUID().toString())
                .username(username)
                .email(email)
                .passwordHash(passwordHash)
                .role(UserRole.ADMIN)
                .membership(SpaceMembership.builder().spaceId(null).role(UserRole.ADMIN).build())
                .build();
        save(admin);
        return admin;
    }

    private void writeUsers(List<WikiUser> users) {
        StringBuilder builder = new StringBuilder();
        for (WikiUser user : users) {
            builder.append(formatLine(user)).append('\n');
        }
        try {
            Files.writeString(
                    getUsersFilePath(),
                    builder.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write users", exception);
        }
    }

    private WikiUser parseLine(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 5 && parts.length != 6) {
            throw new IllegalStateException("Invalid user record: " + line);
        }
        UserRole role = UserRole.valueOf(parts[4]);
        List<SpaceMembership> memberships = parts.length == 6 ? parseMemberships(parts[5]) : List.of();
        WikiUser.WikiUserBuilder builder = WikiUser.builder()
                .id(parts[0])
                .username(parts[1])
                .email(parts[2])
                .passwordHash(parts[3])
                .role(role);
        for (SpaceMembership membership : memberships) {
            builder.membership(membership);
        }
        return builder.build();
    }

    private String formatLine(WikiUser user) {
        return String.join(
                "|",
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getRole().name(),
                formatMemberships(user.getMemberships()));
    }

    private List<SpaceMembership> parseMemberships(String raw) {
        if (raw == null || raw.isBlank() || NO_MEMBERSHIPS.equals(raw)) {
            return List.of();
        }
        return Arrays.stream(raw.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(entry -> {
                    String[] parts = entry.split(":", 2);
                    if (parts.length != 2) {
                        throw new IllegalStateException("Invalid membership token: " + entry);
                    }
                    String spaceId = "*".equals(parts[0]) || parts[0].isBlank() ? null : parts[0];
                    return SpaceMembership.builder()
                            .spaceId(spaceId)
                            .role(UserRole.valueOf(parts[1]))
                            .build();
                })
                .toList();
    }

    private String formatMemberships(List<SpaceMembership> memberships) {
        if (memberships == null || memberships.isEmpty()) {
            return NO_MEMBERSHIPS;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < memberships.size(); i++) {
            SpaceMembership membership = memberships.get(i);
            if (i > 0) {
                builder.append(';');
            }
            builder.append(membership.isGlobal() ? "*" : membership.getSpaceId())
                    .append(':')
                    .append(membership.getRole().name());
        }
        return builder.toString();
    }

    private Path getUsersFilePath() {
        return wikiProperties.getStorageRoot().resolve(".auth").resolve(USERS_FILE_NAME);
    }
}
