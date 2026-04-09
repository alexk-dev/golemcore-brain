package dev.golemcore.brain.domain;

import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiTreeNode {
    String path;
    String parentPath;
    String title;
    String slug;
    WikiNodeKind kind;
    boolean hasChildren;
    List<WikiTreeNode> children;
}
