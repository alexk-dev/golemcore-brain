package dev.golemcore.brain.adapter.out.filesystem.auth;

import dev.golemcore.brain.application.port.out.auth.SessionRepository;
import dev.golemcore.brain.config.WikiProperties;
import dev.golemcore.brain.domain.auth.UserSession;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileSessionRepository implements SessionRepository {

    private static final String SESSIONS_FILE_NAME = "sessions.jsonl";

    private final WikiProperties wikiProperties;

    @PostConstruct
    public void initialize() {
        Path filePath = getSessionsFilePath();
        try {
            Files.createDirectories(filePath.getParent());
            if (!Files.exists(filePath)) {
                Files.writeString(filePath, "", StandardCharsets.UTF_8, StandardOpenOption.CREATE);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize session storage", exception);
        }
    }

    @Override
    public Optional<UserSession> findByToken(String token) {
        return listSessions().stream()
                .filter(session -> session.getToken().equals(token))
                .filter(session -> session.getExpiresAt().isAfter(Instant.now()))
                .findFirst();
    }

    @Override
    public UserSession save(UserSession session) {
        List<UserSession> sessions = new ArrayList<>(listSessions());
        sessions.removeIf(existing -> existing.getToken().equals(session.getToken()));
        sessions.add(session);
        writeSessions(sessions);
        return session;
    }

    @Override
    public void delete(String token) {
        List<UserSession> sessions = new ArrayList<>(listSessions());
        sessions.removeIf(session -> session.getToken().equals(token));
        writeSessions(sessions);
    }

    @Override
    public void deleteByUserId(String userId) {
        List<UserSession> sessions = new ArrayList<>(listSessions());
        sessions.removeIf(session -> session.getUserId().equals(userId));
        writeSessions(sessions);
    }

    private List<UserSession> listSessions() {
        try {
            if (!Files.exists(getSessionsFilePath())) {
                return List.of();
            }
            List<UserSession> sessions = new ArrayList<>();
            for (String line : Files.readAllLines(getSessionsFilePath(), StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                sessions.add(parseLine(line));
            }
            return sessions;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read sessions", exception);
        }
    }

    private void writeSessions(List<UserSession> sessions) {
        StringBuilder builder = new StringBuilder();
        for (UserSession session : sessions) {
            builder.append(formatLine(session)).append('\n');
        }
        try {
            Files.writeString(
                    getSessionsFilePath(),
                    builder.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write sessions", exception);
        }
    }

    private UserSession parseLine(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 3) {
            throw new IllegalStateException("Invalid session record: " + line);
        }
        return UserSession.builder()
                .token(parts[0])
                .userId(parts[1])
                .expiresAt(Instant.parse(parts[2]))
                .build();
    }

    private String formatLine(UserSession session) {
        return String.join("|", session.getToken(), session.getUserId(), session.getExpiresAt().toString());
    }

    private Path getSessionsFilePath() {
        return wikiProperties.getStorageRoot().resolve(".auth").resolve(SESSIONS_FILE_NAME);
    }
}
