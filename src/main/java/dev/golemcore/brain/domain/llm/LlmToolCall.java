package dev.golemcore.brain.domain.llm;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmToolCall {
    private String id;
    private String name;
    private Map<String, Object> arguments;
}
