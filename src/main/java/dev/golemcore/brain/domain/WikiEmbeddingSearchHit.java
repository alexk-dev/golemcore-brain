package dev.golemcore.brain.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiEmbeddingSearchHit {
    String id;
    String path;
    String title;
    String excerpt;
    String parentPath;
    WikiNodeKind kind;
    double score;
}
