package dev.golemcore.brain.domain.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModelRegistryConfig {
    private String repositoryUrl;
    private String branch;
}
