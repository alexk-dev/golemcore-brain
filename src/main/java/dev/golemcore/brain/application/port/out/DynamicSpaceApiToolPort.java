package dev.golemcore.brain.application.port.out;

import dev.golemcore.brain.domain.llm.LlmToolCall;
import dev.golemcore.brain.domain.llm.LlmToolDefinition;
import dev.golemcore.brain.domain.llm.LlmToolResult;
import java.util.List;

public interface DynamicSpaceApiToolPort {
    List<LlmToolDefinition> definitions();

    LlmToolResult execute(String spaceId, LlmToolCall toolCall);
}
