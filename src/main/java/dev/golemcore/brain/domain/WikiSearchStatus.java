package dev.golemcore.brain.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiSearchStatus {
    String mode;
    boolean ready;
    int indexedDocuments;
    String lastUpdatedAt;
}
