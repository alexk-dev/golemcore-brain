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

package me.golemcore.brain.adapter.out.index.memory;

import me.golemcore.brain.application.port.out.WikiEmbeddingIndexPort;
import me.golemcore.brain.domain.WikiDocumentChangeSet;
import me.golemcore.brain.domain.WikiEmbeddingDocument;
import me.golemcore.brain.domain.WikiEmbeddingSearchHit;
import me.golemcore.brain.domain.WikiIndexedDocument;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * In-memory embedding index. Primary purpose: reference implementation for the
 * pluggable {@link WikiEmbeddingIndexPort} abstraction and a zero-dependency
 * default for tests and ephemeral deployments. Not durable — all state is lost
 * on process restart.
 */
@Component
@ConditionalOnProperty(name = "brain.indexing.embedding-adapter", havingValue = "in-memory")
public class InMemoryWikiEmbeddingIndexAdapter implements WikiEmbeddingIndexPort {

    private final Map<String, Map<ChunkKey, StoredChunk>> spaces = new ConcurrentHashMap<>();

    @Override
    public synchronized void applyChanges(String spaceId, WikiDocumentChangeSet changeSet) {
        if (changeSet == null || spaceId == null || spaceId.isBlank()) {
            return;
        }
        Map<ChunkKey, StoredChunk> space = spaces.computeIfAbsent(spaceId, key -> new LinkedHashMap<>());
        if (changeSet.isFullRebuild()) {
            space.clear();
        }
        if (changeSet.getDeletedPaths() != null) {
            for (String deletedPath : changeSet.getDeletedPaths()) {
                space.keySet().removeIf(key -> key.path().equals(deletedPath));
            }
        }
        if (changeSet.getEmbeddingUpserts() == null || changeSet.getEmbeddingUpserts().isEmpty()) {
            return;
        }
        Set<String> wipedPaths = new HashSet<>();
        for (WikiEmbeddingDocument embeddingDocument : changeSet.getEmbeddingUpserts()) {
            WikiIndexedDocument document = embeddingDocument.getDocument();
            String path = document.getPath();
            if (wipedPaths.add(path)) {
                space.keySet().removeIf(key -> key.path().equals(path));
            }
            space.put(new ChunkKey(path, embeddingDocument.getChunkIndex()),
                    new StoredChunk(document, embeddingDocument.getChunkIndex(), embeddingDocument.getChunkText(),
                            embeddingDocument.getEmbeddingModelId(), embeddingDocument.getVector()));
        }
    }

    @Override
    public synchronized Optional<String> findStoredEmbeddingModelId(String spaceId) {
        Map<ChunkKey, StoredChunk> space = spaces.get(spaceId);
        if (space == null) {
            return Optional.empty();
        }
        return space.values().stream()
                .map(StoredChunk::embeddingModelId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst();
    }

    @Override
    public synchronized List<WikiEmbeddingSearchHit> search(String spaceId, List<Double> embedding, int limit) {
        if (embedding == null || embedding.isEmpty()) {
            return List.of();
        }
        Map<ChunkKey, StoredChunk> space = spaces.get(spaceId);
        if (space == null || space.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, WikiEmbeddingSearchHit> bestByPath = new LinkedHashMap<>();
        for (StoredChunk chunk : space.values()) {
            double score = cosineSimilarity(chunk.vector(), embedding);
            WikiEmbeddingSearchHit hit = WikiEmbeddingSearchHit.builder()
                    .id(chunk.document().getId())
                    .path(chunk.document().getPath())
                    .title(chunk.document().getTitle())
                    .excerpt(chunk.chunkText() == null ? chunk.document().getBody() : chunk.chunkText())
                    .parentPath(chunk.document().getParentPath())
                    .kind(chunk.document().getKind())
                    .score(score)
                    .build();
            WikiEmbeddingSearchHit current = bestByPath.get(hit.getPath());
            if (current == null || hit.getScore() > current.getScore()) {
                bestByPath.put(hit.getPath(), hit);
            }
        }
        return bestByPath.values().stream()
                .sorted(Comparator.comparingDouble(WikiEmbeddingSearchHit::getScore).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public synchronized List<String> listIndexedPaths(String spaceId) {
        Map<ChunkKey, StoredChunk> space = spaces.get(spaceId);
        if (space == null) {
            return List.of();
        }
        return space.keySet().stream()
                .map(ChunkKey::path)
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public synchronized Map<String, String> listIndexedRevisions(String spaceId) {
        Map<ChunkKey, StoredChunk> space = spaces.get(spaceId);
        if (space == null) {
            return Map.of();
        }
        LinkedHashMap<String, String> revisions = new LinkedHashMap<>();
        for (StoredChunk chunk : space.values()) {
            revisions.putIfAbsent(chunk.document().getPath(), chunk.document().getRevision());
        }
        return revisions;
    }

    @Override
    public synchronized int count(String spaceId) {
        Map<ChunkKey, StoredChunk> space = spaces.get(spaceId);
        if (space == null) {
            return 0;
        }
        return (int) space.keySet().stream().map(ChunkKey::path).distinct().count();
    }

    private double cosineSimilarity(List<Double> left, List<Double> right) {
        if (left.size() != right.size()) {
            throw new IllegalStateException("Embedding dimension mismatch: stored=" + left.size()
                    + " query=" + right.size()
                    + " — the embedding model likely changed; rebuild the embedding index.");
        }
        int size = left.size();
        if (size == 0) {
            return 0.0d;
        }
        double dot = 0.0d;
        double leftMagnitude = 0.0d;
        double rightMagnitude = 0.0d;
        for (int index = 0; index < size; index++) {
            double leftValue = left.get(index) == null ? 0.0d : left.get(index);
            double rightValue = right.get(index) == null ? 0.0d : right.get(index);
            dot += leftValue * rightValue;
            leftMagnitude += leftValue * leftValue;
            rightMagnitude += rightValue * rightValue;
        }
        if (leftMagnitude == 0.0d || rightMagnitude == 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(leftMagnitude) * Math.sqrt(rightMagnitude));
    }

    private record ChunkKey(String path, int chunkIndex) {
    }

    private record StoredChunk(WikiIndexedDocument document, int chunkIndex, String chunkText,
            String embeddingModelId, List<Double> vector) {
        private StoredChunk {
            vector = vector == null ? List.of() : List.copyOf(vector);
        }
    }
}
