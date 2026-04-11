package dev.golemcore.brain.domain;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiDocumentChangeSet {
    String spaceId;
    List<WikiIndexedDocument> upserts;
    List<WikiEmbeddingDocument> embeddingUpserts;
    List<String> deletedPaths;
    boolean fullRebuild;

    public boolean isEmpty() {
        boolean hasUpserts = upserts != null && !upserts.isEmpty();
        boolean hasEmbeddingUpserts = embeddingUpserts != null && !embeddingUpserts.isEmpty();
        boolean hasDeletes = deletedPaths != null && !deletedPaths.isEmpty();
        return !fullRebuild && !hasUpserts && !hasEmbeddingUpserts && !hasDeletes;
    }
}
