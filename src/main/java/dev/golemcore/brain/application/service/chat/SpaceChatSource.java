package dev.golemcore.brain.application.service.chat;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SpaceChatSource {
    String path;
    String title;
    String excerpt;
}
