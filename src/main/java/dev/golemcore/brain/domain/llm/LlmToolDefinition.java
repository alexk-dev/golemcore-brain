package dev.golemcore.brain.domain.llm;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LlmToolDefinition {
    String name;
    String description;
    Map<String, Object> inputSchema;
}
