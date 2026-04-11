package dev.golemcore.brain.domain.dynamicapi;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DynamicSpaceApiConfig {
    private String id;
    private String slug;
    private String name;
    private String description;
    private String modelConfigId;
    private String systemPrompt;
    private Boolean enabled;
    private Integer maxIterations;
    private Instant createdAt;
    private Instant updatedAt;
}
