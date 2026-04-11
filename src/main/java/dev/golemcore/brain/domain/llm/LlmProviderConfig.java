package dev.golemcore.brain.domain.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.golemcore.brain.domain.Secret;
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
public class LlmProviderConfig {
    private Secret apiKey;
    private String baseUrl;
    private Integer requestTimeoutSeconds;
    private LlmApiType apiType;
    private Instant createdAt;
    private Instant updatedAt;
}
