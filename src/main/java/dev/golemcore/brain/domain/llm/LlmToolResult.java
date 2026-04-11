package dev.golemcore.brain.domain.llm;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LlmToolResult {
    @SuppressWarnings("PMD.AvoidFieldNameMatchingMethodName")
    boolean success;
    String output;
    Object data;
    String error;

    public static LlmToolResult success(String output, Object data) {
        return LlmToolResult.builder()
                .success(true)
                .output(output)
                .data(data)
                .build();
    }

    public static LlmToolResult failure(String error) {
        return LlmToolResult.builder()
                .success(false)
                .error(error)
                .build();
    }
}
