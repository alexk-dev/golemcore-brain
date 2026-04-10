package dev.golemcore.brain.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiPageHistoryEntry {
    String id;
    String title;
    String slug;
    String recordedAt;
}
