package dev.golemcore.brain.domain;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiIndexMetadata {
    int documentCount;
    int embeddingDocumentCount;
    Instant lastUpdatedAt;
    boolean ready;
    boolean embeddingsReady;
}
