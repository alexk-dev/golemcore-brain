package dev.golemcore.brain.domain.llm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelCatalogEntry {
    private String provider;
    private String displayName;
    private Boolean supportsVision = true;
    private Boolean supportsTemperature = true;
    private Integer maxInputTokens = 128000;
    private ModelReasoningProfile reasoning;

    public ModelCatalogEntry withProvider(String resolvedProvider) {
        return new ModelCatalogEntry(
                resolvedProvider,
                displayName,
                supportsVision,
                supportsTemperature,
                maxInputTokens,
                reasoning);
    }
}
