package dev.golemcore.brain.domain.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmSettings {

    @Builder.Default
    private Map<String, LlmProviderConfig> providers = new LinkedHashMap<>();

    @Builder.Default
    private List<LlmModelConfig> models = new ArrayList<>();
}
