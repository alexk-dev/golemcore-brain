package dev.golemcore.brain.domain;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiIndexStatus {
    String mode;
    boolean ready;
    int indexedDocuments;
    int embeddingDocuments;
    Instant lastUpdatedAt;
    boolean embeddingsReady;
}
