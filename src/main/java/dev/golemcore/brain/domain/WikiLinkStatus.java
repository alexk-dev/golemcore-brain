package dev.golemcore.brain.domain;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiLinkStatus {
    List<WikiLinkStatusItem> backlinks;
    List<WikiLinkStatusItem> brokenIncoming;
    List<WikiLinkStatusItem> outgoings;
    List<WikiLinkStatusItem> brokenOutgoings;
}
