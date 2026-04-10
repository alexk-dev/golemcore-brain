package dev.golemcore.brain.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiPageHistoryVersion {
    String id;
    String title;
    String slug;
    String content;
    String recordedAt;
    String author;
    String reason;
    String summary;
}
