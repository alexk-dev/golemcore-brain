package dev.golemcore.brain.application.port.out;

import dev.golemcore.brain.domain.WikiDocumentChangeSet;
import dev.golemcore.brain.domain.WikiEmbeddingSearchHit;
import java.util.List;
import java.util.Map;

public interface WikiEmbeddingIndexPort {
    void applyChanges(String spaceId, WikiDocumentChangeSet changeSet);

    List<WikiEmbeddingSearchHit> search(String spaceId, List<Double> embedding, int limit);

    List<String> listIndexedPaths(String spaceId);

    Map<String, String> listIndexedRevisions(String spaceId);

    int count(String spaceId);
}
