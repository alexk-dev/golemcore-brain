package dev.golemcore.brain.adapter.out.filesystem.llm;

import dev.golemcore.brain.application.port.out.ModelRegistryCachePort;
import dev.golemcore.brain.config.WikiProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class FileModelRegistryCacheAdapter implements ModelRegistryCachePort {

    private static final String CACHE_DIRECTORY = ".llm/model-registry-cache";
    private static final Pattern SAFE_RELATIVE_PATH = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._/-]*[a-zA-Z0-9.]$");

    private final WikiProperties wikiProperties;
    private final ObjectMapper objectMapper;

    @Override
    public CachedRegistryEntry read(String repositoryUrl, String branch, String relativePath) {
        Path path = cachePath(repositoryUrl, branch, relativePath);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return null;
            }
            CacheEntry entry = objectMapper.readValue(json, CacheEntry.class);
            return new CachedRegistryEntry(entry.getCachedAt(), entry.isFound(), entry.getContent());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse model registry cache entry " + relativePath, exception);
        }
    }

    @Override
    public void write(String repositoryUrl, String branch, String relativePath, CachedRegistryEntry entry) {
        Path path = cachePath(repositoryUrl, branch, relativePath);
        try {
            Files.createDirectories(path.getParent());
            CacheEntry payload = new CacheEntry(entry.cachedAt(), entry.found(), entry.content());
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            Files.writeString(path, json + "\n", StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to serialize model registry cache entry " + relativePath,
                    exception);
        }
    }

    private Path cachePath(String repositoryUrl, String branch, String relativePath) {
        String safeRelativePath = safeRelativePath(relativePath);
        Path cacheRoot = wikiProperties.getStorageRoot()
                .resolve(CACHE_DIRECTORY)
                .resolve(sha256Hex(repositoryUrl + "\n" + branch))
                .normalize();
        Path resolved = cacheRoot.resolve(safeRelativePath + ".cache.json").normalize();
        if (!resolved.startsWith(cacheRoot)) {
            throw new IllegalArgumentException("Model registry cache path escapes cache root");
        }
        return resolved;
    }

    private String safeRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Model registry cache path is required");
        }
        String normalized = relativePath.trim().replace('\\', '/');
        if (normalized.contains("..")
                || normalized.startsWith("/")
                || !SAFE_RELATIVE_PATH.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Model registry cache path is invalid");
        }
        return normalized;
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                builder.append(String.format("%02x", current & 0xff));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class CacheEntry {
        private java.time.Instant cachedAt;
        private boolean found;
        private String content;
    }
}
