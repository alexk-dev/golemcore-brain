package dev.golemcore.brain.domain.llm;

import java.util.Locale;

public enum LlmReasoningEffort {
    NONE("none"), LOW("low"), MEDIUM("medium"), HIGH("high"), XHIGH("xhigh");

    private final String value;

    LlmReasoningEffort(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static LlmReasoningEffort fromJson(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String normalized = source.trim().toLowerCase(Locale.ROOT);
        for (LlmReasoningEffort effort : values()) {
            if (effort.value.equals(normalized) || effort.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return effort;
            }
        }
        throw new IllegalArgumentException("Unsupported LLM reasoning effort: " + source);
    }
}
