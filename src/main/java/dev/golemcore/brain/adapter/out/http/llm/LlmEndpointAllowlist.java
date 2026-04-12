package dev.golemcore.brain.adapter.out.http.llm;

import java.net.URI;
import java.util.Locale;

final class LlmEndpointAllowlist {

    private LlmEndpointAllowlist() {
    }

    static String canonicalBaseUrl(String configuredBaseUrl, String fallbackBaseUrl) {
        URI uri = URI.create(defaultIfBlank(configuredBaseUrl, fallbackBaseUrl));
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("LLM endpoint must use HTTPS");
        }
        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("LLM endpoint host is required");
        }
        return switch (host.toLowerCase(Locale.ROOT)) {
        case "api.openai.com" -> "https://api.openai.com/v1";
        case "openrouter.ai" -> "https://openrouter.ai/api/v1";
        case "api.anthropic.com" -> "https://api.anthropic.com";
        case "generativelanguage.googleapis.com" -> "https://generativelanguage.googleapis.com/v1beta";
        case "api.groq.com" -> "https://api.groq.com/openai/v1";
        case "api.deepseek.com" -> "https://api.deepseek.com/v1";
        case "api.mistral.ai" -> "https://api.mistral.ai/v1";
        case "api.x.ai" -> "https://api.x.ai/v1";
        default -> throw new IllegalArgumentException("LLM endpoint host is not allowed");
        };
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
