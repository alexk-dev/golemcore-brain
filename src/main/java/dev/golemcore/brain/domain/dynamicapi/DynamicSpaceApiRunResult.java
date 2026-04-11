package dev.golemcore.brain.domain.dynamicapi;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DynamicSpaceApiRunResult {
    String apiId;
    String apiSlug;
    Object result;
    String rawResponse;
    int iterations;
    int toolCallCount;
}
