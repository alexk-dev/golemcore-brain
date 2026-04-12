package dev.golemcore.brain.adapter.out.http.llm;

import java.net.URI;
import java.util.Locale;

final class LlmEndpointResolver {

    private LlmEndpointResolver() {
    }

    static String canonicalBaseUrl(String configuredBaseUrl, String fallbackBaseUrl) {
        String rawBaseUrl = defaultIfBlank(configuredBaseUrl, fallbackBaseUrl);
        URI uri = URI.create(rawBaseUrl);
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("LLM endpoint must use HTTP or HTTPS");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("LLM endpoint host is required");
        }
        String canonicalKnownBaseUrl = canonicalKnownBaseUrl(host);
        if (canonicalKnownBaseUrl != null && (isBlank(configuredBaseUrl) || isRootPath(uri))) {
            return canonicalKnownBaseUrl;
        }
        return trimTrailingSlash(rawBaseUrl);
    }

    private static String canonicalKnownBaseUrl(String host) {
        return switch (host.toLowerCase(Locale.ROOT)) {
        case "api.openai.com" -> "https://api.openai.com/v1";
        case "openrouter.ai" -> "https://openrouter.ai/api/v1";
        case "api.anthropic.com" -> "https://api.anthropic.com";
        case "generativelanguage.googleapis.com" -> "https://generativelanguage.googleapis.com/v1beta";
        case "api.groq.com" -> "https://api.groq.com/openai/v1";
        case "api.deepseek.com" -> "https://api.deepseek.com/v1";
        case "api.mistral.ai" -> "https://api.mistral.ai/v1";
        case "api.x.ai" -> "https://api.x.ai/v1";
        default -> null;
        };
    }

    private static String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isRootPath(URI uri) {
        String path = uri.getPath();
        return path == null || path.isBlank() || "/".equals(path);
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
