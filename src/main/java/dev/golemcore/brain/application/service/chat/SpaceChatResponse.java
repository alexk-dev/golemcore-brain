package dev.golemcore.brain.application.service.chat;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SpaceChatResponse {
    String answer;
    String modelConfigId;
    String summary;
    boolean compacted;
    List<SpaceChatSource> sources;
}
