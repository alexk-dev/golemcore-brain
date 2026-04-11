package dev.golemcore.brain.domain.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmModelConfig {
    private String id;
    private String provider;
    private String modelId;
    private String displayName;
    private LlmModelKind kind;
    private Boolean enabled;
    private Integer maxInputTokens;
    private Integer dimensions;
    private Double temperature;
    private Instant createdAt;
    private Instant updatedAt;
}
