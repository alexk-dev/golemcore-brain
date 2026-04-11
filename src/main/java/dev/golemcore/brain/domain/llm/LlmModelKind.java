package dev.golemcore.brain.domain.llm;

import java.util.Locale;

public enum LlmModelKind {
    CHAT("chat"), EMBEDDING("embedding");

    private final String value;

    LlmModelKind(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static LlmModelKind fromJson(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String normalized = source.trim().toLowerCase(Locale.ROOT);
        for (LlmModelKind kind : values()) {
            if (kind.value.equals(normalized) || kind.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unsupported LLM model kind: " + source);
    }
}
