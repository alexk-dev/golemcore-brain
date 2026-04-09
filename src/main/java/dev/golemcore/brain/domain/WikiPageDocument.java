package dev.golemcore.brain.domain;

import java.time.Instant;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiPageDocument {
    String id;
    String path;
    String parentPath;
    String slug;
    String title;
    String body;
    WikiNodeKind kind;
    Instant createdAt;
    Instant updatedAt;
}
