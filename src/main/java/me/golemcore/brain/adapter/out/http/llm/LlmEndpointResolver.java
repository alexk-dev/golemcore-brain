/*
 * Copyright 2026 Aleksei Kuleshov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contact: alex@kuleshov.tech
 */

package me.golemcore.brain.adapter.out.http.llm;

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
        case "api.anthropic.com" -> "https://api.anthropic.com/v1";
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
