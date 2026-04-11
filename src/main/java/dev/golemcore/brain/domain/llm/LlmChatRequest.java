package dev.golemcore.brain.domain.llm;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmChatRequest {
    private LlmProviderConfig provider;
    private LlmModelConfig model;
    private String systemPrompt;

    @Builder.Default
    private List<LlmChatMessage> messages = new ArrayList<>();

    @Builder.Default
    private List<LlmToolDefinition> tools = new ArrayList<>();
}
