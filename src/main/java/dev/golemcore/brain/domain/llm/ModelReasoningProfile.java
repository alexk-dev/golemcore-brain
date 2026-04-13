package dev.golemcore.brain.domain.llm;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelReasoningProfile {
    private String defaultLevel;

    private Map<String, ModelReasoningLevel> levels = new LinkedHashMap<>();

    public String getDefault() {
        return defaultLevel;
    }

    public void setDefault(String defaultValue) {
        this.defaultLevel = defaultValue;
    }
}
