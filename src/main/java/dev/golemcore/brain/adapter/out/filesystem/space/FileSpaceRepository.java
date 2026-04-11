package dev.golemcore.brain.adapter.out.filesystem.space;

import dev.golemcore.brain.application.port.out.SpaceRepository;
import dev.golemcore.brain.config.WikiProperties;
import dev.golemcore.brain.domain.space.Space;
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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileSpaceRepository implements SpaceRepository {

    private static final String SPACES_FILE = "spaces.jsonl";

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
            if (listSpaces().isEmpty()) {
                Space defaultSpace = Space.builder()
                        .id(UUID.randomUUID().toString())
                        .slug(wikiProperties.getDefaultSpaceSlug())
                        .name(wikiProperties.getDefaultSpaceName())
                        .createdAt(Instant.now())
                        .build();
                save(defaultSpace);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize spaces storage", exception);
        }
    }

    @Override
    public List<Space> listSpaces() {
        try {
            Path filePath = getFilePath();
            if (!Files.exists(filePath)) {
                return List.of();
            }
            List<Space> spaces = new ArrayList<>();
            for (String line : Files.readAllLines(filePath, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                spaces.add(parseLine(line));
            }
            return spaces;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read spaces", exception);
        }
    }

    @Override
    public Optional<Space> findById(String id) {
        return listSpaces().stream().filter(space -> space.getId().equals(id)).findFirst();
    }

    @Override
    public Optional<Space> findBySlug(String slug) {
        return listSpaces().stream().filter(space -> space.getSlug().equals(slug)).findFirst();
    }

    @Override
    public Space save(Space space) {
        List<Space> spaces = new ArrayList<>(listSpaces());
        spaces.removeIf(existing -> existing.getId().equals(space.getId()));
        spaces.add(space);
        writeSpaces(spaces);
        return space;
    }

    @Override
    public void delete(String id) {
        List<Space> spaces = new ArrayList<>(listSpaces());
        spaces.removeIf(space -> space.getId().equals(id));
        writeSpaces(spaces);
    }

    private void writeSpaces(List<Space> spaces) {
        StringBuilder builder = new StringBuilder();
        for (Space space : spaces) {
            builder.append(formatLine(space)).append('\n');
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
            throw new IllegalStateException("Failed to write spaces", exception);
        }
    }

    private Space parseLine(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != 4) {
            throw new IllegalStateException("Invalid space record: " + line);
        }
        return Space.builder()
                .id(parts[0])
                .slug(parts[1])
                .name(parts[2])
                .createdAt(Instant.parse(parts[3]))
                .build();
    }

    private String formatLine(Space space) {
        return String.join(
                "|",
                space.getId(),
                space.getSlug(),
                space.getName(),
                space.getCreatedAt().toString());
    }

    private Path getFilePath() {
        return wikiProperties.getStorageRoot().resolve(SPACES_FILE);
    }
}
