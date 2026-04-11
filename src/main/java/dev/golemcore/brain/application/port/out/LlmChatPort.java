package dev.golemcore.brain.application.port.out;

import dev.golemcore.brain.domain.llm.LlmChatRequest;
import dev.golemcore.brain.domain.llm.LlmChatResponse;

public interface LlmChatPort {
    LlmChatResponse chat(LlmChatRequest request);
}
