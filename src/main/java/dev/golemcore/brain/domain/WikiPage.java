package dev.golemcore.brain.domain;

import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiPage {
    String path;
    String parentPath;
    String title;
    String slug;
    WikiNodeKind kind;
    String content;
    String createdAt;
    String updatedAt;
    List<WikiTreeNode> children;
}
