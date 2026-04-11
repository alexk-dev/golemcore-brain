package dev.golemcore.brain.adapter.out.filesystem.llm;

import dev.golemcore.brain.application.port.out.LlmSettingsRepository;
import dev.golemcore.brain.config.WikiProperties;
import dev.golemcore.brain.domain.llm.LlmSettings;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class FileLlmSettingsRepository implements LlmSettingsRepository {

    private static final String DIRECTORY_NAME = ".llm";
    private static final String FILE_NAME = "settings.json";

    private final WikiProperties wikiProperties;
    private final ObjectMapper objectMapper;

    @PostConstruct
    @Override
    public synchronized void initialize() {
        Path filePath = getFilePath();
        try {
            Files.createDirectories(filePath.getParent());
            if (!Files.exists(filePath)) {
                writeSettings(LlmSettings.builder().build());
            } else {
                secureFile(filePath);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize LLM settings storage", exception);
        }
    }

    @Override
    public synchronized LlmSettings load() {
        Path filePath = getFilePath();
        try {
            if (!Files.exists(filePath)) {
                return save(LlmSettings.builder().build());
            }
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return LlmSettings.builder().build();
            }
            LlmSettings settings = objectMapper.readValue(content, LlmSettings.class);
            return settings != null ? settings : LlmSettings.builder().build();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read LLM settings", exception);
        }
    }

    @Override
    public synchronized LlmSettings save(LlmSettings settings) {
        LlmSettings safeSettings = settings != null ? settings : LlmSettings.builder().build();
        try {
            writeSettings(safeSettings);
            return safeSettings;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write LLM settings", exception);
        }
    }

    private void writeSettings(LlmSettings settings) throws IOException {
        Path filePath = getFilePath();
        Files.createDirectories(filePath.getParent());
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(settings);
        Files.writeString(
                filePath,
                json + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        secureFile(filePath);
    }

    private void secureFile(Path filePath) {
        try {
            Files.setPosixFilePermissions(filePath, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        } catch (IOException | UnsupportedOperationException ignored) {
            // Best-effort hardening. Some filesystems do not expose POSIX permissions.
        }
    }

    private Path getFilePath() {
        return wikiProperties.getStorageRoot().resolve(DIRECTORY_NAME).resolve(FILE_NAME);
    }
}
