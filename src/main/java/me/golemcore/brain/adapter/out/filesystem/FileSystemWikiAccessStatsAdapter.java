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

package me.golemcore.brain.adapter.out.filesystem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import me.golemcore.brain.application.port.out.WikiAccessStatsPort;
import me.golemcore.brain.config.WikiProperties;
import me.golemcore.brain.domain.WikiAccessStats;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FileSystemWikiAccessStatsAdapter implements WikiAccessStatsPort {

    private static final String SPACES_DIR = "spaces";
    private static final String STATS_FILE = ".access.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern SPACE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");

    private final WikiProperties wikiProperties;
    private final Clock clock;

    private final Map<String, Map<String, WikiAccessStats>> cache = new ConcurrentHashMap<>();
    // Per-space locks are independent of any service-level lock because the port is
    // also called outside mutation locks — notably from getPage.recordAccess.
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public FileSystemWikiAccessStatsAdapter(WikiProperties wikiProperties, Clock clock) {
        this.wikiProperties = wikiProperties;
        this.clock = clock;
    }

    @Override
    public WikiAccessStats recordAccess(String spaceId, String path) {
        validateSpaceId(spaceId);
        ReentrantLock lock = locks.computeIfAbsent(spaceId, id -> new ReentrantLock());
        lock.lock();
        try {
            Map<String, WikiAccessStats> stats = loadStats(spaceId);
            WikiAccessStats previous = stats.get(path);
            long count = (previous == null ? 0 : previous.getAccessCount()) + 1;
            WikiAccessStats next = WikiAccessStats.builder()
                    .path(path)
                    .accessCount(count)
                    .lastAccessedAt(Instant.now(clock))
                    .build();
            Map<String, WikiAccessStats> snapshot = new HashMap<>(stats);
            snapshot.put(path, next);
            persist(spaceId, snapshot);
            stats.put(path, next);
            return next;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<WikiAccessStats> getStats(String spaceId, String path) {
        validateSpaceId(spaceId);
        return Optional.ofNullable(loadStats(spaceId).get(path));
    }

    @Override
    public List<WikiAccessStats> listTop(String spaceId, int limit) {
        validateSpaceId(spaceId);
        return loadStats(spaceId).values().stream()
                .sorted(Comparator.comparingLong(WikiAccessStats::getAccessCount).reversed()
                        .thenComparing(WikiAccessStats::getLastAccessedAt, Comparator.reverseOrder()))
                .limit(limit <= 0 ? Long.MAX_VALUE : limit)
                .toList();
    }

    @Override
    public void removePath(String spaceId, String path) {
        validateSpaceId(spaceId);
        if (path == null) {
            return;
        }
        ReentrantLock lock = locks.computeIfAbsent(spaceId, id -> new ReentrantLock());
        lock.lock();
        try {
            Map<String, WikiAccessStats> stats = loadStats(spaceId);
            if (!stats.containsKey(path)) {
                return;
            }
            Map<String, WikiAccessStats> snapshot = new HashMap<>(stats);
            snapshot.remove(path);
            persist(spaceId, snapshot);
            stats.remove(path);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void removePaths(String spaceId, Collection<String> paths) {
        validateSpaceId(spaceId);
        if (paths == null || paths.isEmpty()) {
            return;
        }
        ReentrantLock lock = locks.computeIfAbsent(spaceId, id -> new ReentrantLock());
        lock.lock();
        try {
            Map<String, WikiAccessStats> stats = loadStats(spaceId);
            Map<String, WikiAccessStats> snapshot = new HashMap<>(stats);
            boolean changed = false;
            for (String path : paths) {
                if (path != null && snapshot.remove(path) != null) {
                    changed = true;
                }
            }
            if (!changed) {
                return;
            }
            persist(spaceId, snapshot);
            for (String path : paths) {
                if (path != null) {
                    stats.remove(path);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void removeSubtree(String spaceId, String path) {
        validateSpaceId(spaceId);
        if (path == null || path.isBlank()) {
            return;
        }
        String prefix = path + "/";
        ReentrantLock lock = locks.computeIfAbsent(spaceId, id -> new ReentrantLock());
        lock.lock();
        try {
            Map<String, WikiAccessStats> stats = loadStats(spaceId);
            Map<String, WikiAccessStats> snapshot = new HashMap<>(stats);
            boolean changed = snapshot.keySet().removeIf(key -> key.equals(path) || key.startsWith(prefix));
            if (!changed) {
                return;
            }
            persist(spaceId, snapshot);
            stats.keySet().removeIf(key -> key.equals(path) || key.startsWith(prefix));
        } finally {
            lock.unlock();
        }
    }

    private void validateSpaceId(String spaceId) {
        if (spaceId == null || !SPACE_ID_PATTERN.matcher(spaceId).matches()) {
            throw new IllegalArgumentException("Invalid spaceId: " + spaceId);
        }
    }

    private Map<String, WikiAccessStats> loadStats(String spaceId) {
        return cache.computeIfAbsent(spaceId, id -> readFromDisk(spaceId));
    }

    private Map<String, WikiAccessStats> readFromDisk(String spaceId) {
        Path file = statsFile(spaceId);
        if (!Files.exists(file)) {
            return new ConcurrentHashMap<>();
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            return parse(raw);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read access stats " + file, exception);
        }
    }

    private void persist(String spaceId, Map<String, WikiAccessStats> stats) {
        Path file = statsFile(spaceId);
        Path tmp = file.resolveSibling(STATS_FILE + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(tmp, render(stats), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException cleanup) {
                exception.addSuppressed(cleanup);
            }
            throw new IllegalStateException("Failed to write access stats " + file, exception);
        }
    }

    private Path statsFile(String spaceId) {
        return wikiProperties.getStorageRoot().resolve(SPACES_DIR).resolve(spaceId).resolve(STATS_FILE);
    }

    private String render(Map<String, WikiAccessStats> stats) {
        ObjectNode root = MAPPER.createObjectNode();
        for (WikiAccessStats entry : stats.values()) {
            ObjectNode inner = MAPPER.createObjectNode();
            inner.put("accessCount", entry.getAccessCount());
            inner.put("lastAccessedAt", entry.getLastAccessedAt().toString());
            root.set(entry.getPath(), inner);
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize access stats", exception);
        }
    }

    private Map<String, WikiAccessStats> parse(String raw) {
        Map<String, WikiAccessStats> result = new ConcurrentHashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(raw);
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse access stats JSON; resetting stats for this space", exception);
            return result;
        }
        if (root == null || !root.isObject()) {
            log.warn("Access stats JSON root is not an object; resetting stats for this space");
            return result;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode value = entry.getValue();
            if (value == null || !value.isObject()) {
                continue;
            }
            long count = value.path("accessCount").asLong(0L);
            Instant last = Instant.EPOCH;
            JsonNode lastNode = value.path("lastAccessedAt");
            if (lastNode.isTextual()) {
                try {
                    last = Instant.parse(lastNode.asText());
                } catch (Exception ignored) {
                    last = Instant.EPOCH;
                }
            }
            result.put(entry.getKey(), WikiAccessStats.builder()
                    .path(entry.getKey())
                    .accessCount(count)
                    .lastAccessedAt(last)
                    .build());
        }
        return result;
    }
}
