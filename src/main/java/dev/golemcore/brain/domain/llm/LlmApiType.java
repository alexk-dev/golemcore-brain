package dev.golemcore.brain.domain.llm;

import java.util.Locale;

public enum LlmApiType {
    OPENAI("openai"), ANTHROPIC("anthropic"), GEMINI("gemini");

    private final String value;

    LlmApiType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static LlmApiType fromJson(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String normalized = source.trim().toLowerCase(Locale.ROOT);
        for (LlmApiType type : values()) {
            if (type.value.equals(normalized) || type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported LLM API type: " + source);
    }
}
