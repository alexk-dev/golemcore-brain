package dev.golemcore.brain.domain;

import java.nio.file.Path;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiNodeReference {
    String id;
    String path;
    String parentPath;
    String slug;
    WikiNodeKind kind;
    Path nodePath;
    Path parentDirectory;
    Path markdownPath;
}
