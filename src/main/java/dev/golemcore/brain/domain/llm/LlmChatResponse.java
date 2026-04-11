package dev.golemcore.brain.domain.llm;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LlmChatResponse {
    String content;
    List<LlmToolCall> toolCalls;
    String finishReason;

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
