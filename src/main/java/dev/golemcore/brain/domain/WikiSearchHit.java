package dev.golemcore.brain.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiSearchHit {
    String path;
    String title;
    String excerpt;
    String parentPath;
    WikiNodeKind kind;
}
