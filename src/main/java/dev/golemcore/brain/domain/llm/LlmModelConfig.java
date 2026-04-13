package dev.golemcore.brain.domain.llm;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LlmModelConfig {
    private String id;
    private String provider;
    private String modelId;
    private String displayName;
    private LlmModelKind kind;
    private Boolean enabled;
    private Boolean supportsTemperature;
    private Integer maxInputTokens;
    private Integer dimensions;
    private Double temperature;
    private LlmReasoningEffort reasoningEffort;
    private Instant createdAt;
    private Instant updatedAt;
}
