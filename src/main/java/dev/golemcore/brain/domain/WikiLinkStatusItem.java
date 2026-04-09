package dev.golemcore.brain.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiLinkStatusItem {
    String fromPageId;
    String fromPath;
    String fromTitle;
    String toPageId;
    String toPath;
    String toTitle;
    boolean broken;
}
