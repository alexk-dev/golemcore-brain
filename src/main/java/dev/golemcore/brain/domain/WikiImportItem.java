package dev.golemcore.brain.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiImportItem {
    String path;
    String title;
    WikiNodeKind kind;
    WikiImportAction action;
    WikiImportPolicy policy;
    boolean implicitSection;
    boolean existing;
    boolean selected;
    String sourcePath;
    String note;
}
