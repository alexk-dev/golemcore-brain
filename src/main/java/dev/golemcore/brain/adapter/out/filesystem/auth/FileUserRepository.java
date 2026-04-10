package dev.golemcore.brain.adapter.out.filesystem.auth;

import dev.golemcore.brain.application.port.out.auth.UserRepository;
import dev.golemcore.brain.config.WikiProperties;
import dev.golemcore.brain.domain.auth.UserRole;
import dev.golemcore.brain.domain.auth.WikiUser;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
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

    public WikiUser createAdminIfMissing(String username, String email, String passwordHash) {
        Optional<WikiUser> existingUser = findByUsernameOrEmail(username);
        if (existingUser.isPresent()) {
            return existingUser.get();
        }
        WikiUser admin = WikiUser.builder()
                .id(UUID.randomUUID().toString())
                .username(username)
                .email(email)
                .passwordHash(passwordHash)
                .role(UserRole.ADMIN)
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
        if (parts.length != 5) {
            throw new IllegalStateException("Invalid user record: " + line);
        }
        return WikiUser.builder()
                .id(parts[0])
                .username(parts[1])
                .email(parts[2])
                .passwordHash(parts[3])
                .role(UserRole.valueOf(parts[4]))
                .build();
    }

    private String formatLine(WikiUser user) {
        return String.join(
                "|",
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getRole().name());
    }

    private Path getUsersFilePath() {
        return wikiProperties.getStorageRoot().resolve(".auth").resolve(USERS_FILE_NAME);
    }
}
