package dev.golemcore.brain.domain;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiIndexMetadata {
    int totalDocuments;
    int fullTextIndexedDocuments;
    int embeddingIndexedDocuments;
    int staleDocuments;
    Instant lastUpdatedAt;
    String lastIndexingError;
    String embeddingModelId;
    Instant lastFullRebuildAt;
    boolean ready;
    boolean embeddingsReady;
}
