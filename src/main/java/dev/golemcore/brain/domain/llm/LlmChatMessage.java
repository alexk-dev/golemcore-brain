package dev.golemcore.brain.domain.llm;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmChatMessage {
    private String role;
    private String content;
    private List<LlmToolCall> toolCalls;
    private String toolCallId;
    private String toolName;

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
