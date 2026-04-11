package dev.golemcore.brain.domain;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiIndexedDocument {
    String id;
    String path;
    String parentPath;
    String title;
    String body;
    WikiNodeKind kind;
    Instant updatedAt;
    String revision;

    public WikiSearchDocument toSearchDocument() {
        return WikiSearchDocument.builder()
                .id(id)
                .path(path)
                .parentPath(parentPath)
                .title(title)
                .body(body)
                .kind(kind)
                .build();
    }
}
