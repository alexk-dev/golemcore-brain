package dev.golemcore.brain.domain;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiSemanticSearchResult {
    String mode;
    boolean semanticReady;
    String fallbackReason;
    List<WikiEmbeddingSearchHit> semanticHits;
    List<WikiSearchHit> fallbackHits;
}
