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

package me.golemcore.brain.application.service.support;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import me.golemcore.brain.application.port.out.WikiAccessStatsPort;
import me.golemcore.brain.domain.WikiAccessStats;

public class InMemoryWikiAccessStatsPort implements WikiAccessStatsPort {

    private final Map<String, Map<String, WikiAccessStats>> data = new ConcurrentHashMap<>();

    @Override
    public WikiAccessStats recordAccess(String spaceId, String path) {
        Map<String, WikiAccessStats> stats = data.computeIfAbsent(spaceId, id -> new LinkedHashMap<>());
        WikiAccessStats previous = stats.get(path);
        long count = (previous == null ? 0 : previous.getAccessCount()) + 1;
        WikiAccessStats next = WikiAccessStats.builder()
                .path(path)
                .accessCount(count)
                .lastAccessedAt(Instant.now())
                .build();
        stats.put(path, next);
        return next;
    }

    @Override
    public Optional<WikiAccessStats> getStats(String spaceId, String path) {
        return Optional.ofNullable(data.getOrDefault(spaceId, Map.of()).get(path));
    }

    @Override
    public List<WikiAccessStats> listTop(String spaceId, int limit) {
        return data.getOrDefault(spaceId, Map.of()).values().stream()
                .sorted(Comparator.comparingLong(WikiAccessStats::getAccessCount).reversed())
                .limit(limit <= 0 ? Long.MAX_VALUE : limit)
                .toList();
    }

    @Override
    public void removePath(String spaceId, String path) {
        Map<String, WikiAccessStats> stats = data.get(spaceId);
        if (stats != null) {
            stats.remove(path);
        }
    }

    @Override
    public void removePaths(String spaceId, Collection<String> paths) {
        Map<String, WikiAccessStats> stats = data.get(spaceId);
        if (stats == null || paths == null) {
            return;
        }
        for (String path : paths) {
            if (path != null) {
                stats.remove(path);
            }
        }
    }

    @Override
    public void removeSubtree(String spaceId, String path) {
        Map<String, WikiAccessStats> stats = data.get(spaceId);
        if (stats == null) {
            return;
        }
        stats.keySet().removeIf(key -> key.equals(path) || key.startsWith(path + "/"));
    }
}
