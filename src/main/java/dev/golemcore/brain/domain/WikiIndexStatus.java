package dev.golemcore.brain.domain;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiIndexStatus {
    String mode;
    boolean ready;
    int totalDocuments;
    int fullTextIndexedDocuments;
    int embeddingIndexedDocuments;
    int staleDocuments;
    Instant lastUpdatedAt;
    boolean embeddingsReady;
    String lastIndexingError;
    String embeddingModelId;
    Instant lastFullRebuildAt;
}
