package dev.golemcore.brain.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiSearchStatus {
    String mode;
    boolean ready;
    int indexedDocuments;
    int fullTextIndexedDocuments;
    int embeddingDocuments;
    int embeddingIndexedDocuments;
    int staleDocuments;
    boolean embeddingsReady;
    String lastIndexingError;
    String embeddingModelId;
    String lastFullRebuildAt;
    String lastUpdatedAt;
}
