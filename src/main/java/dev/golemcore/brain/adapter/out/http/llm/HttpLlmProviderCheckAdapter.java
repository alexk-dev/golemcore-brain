package dev.golemcore.brain.adapter.out.http.llm;

import dev.golemcore.brain.application.port.out.LlmProviderCheckPort;
import dev.golemcore.brain.domain.Secret;
import dev.golemcore.brain.domain.llm.LlmApiType;
import dev.golemcore.brain.domain.llm.LlmProviderCheckResult;
import dev.golemcore.brain.domain.llm.LlmProviderConfig;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class HttpLlmProviderCheckAdapter implements LlmProviderCheckPort {

    private static final String USER_AGENT = "golemcore-brain-llm-settings";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(DEFAULT_TIMEOUT)
            .build();

    @Override
    public LlmProviderCheckResult check(String providerName, LlmProviderConfig providerConfig) {
        if (!Secret.hasValue(providerConfig.getApiKey())) {
            return new LlmProviderCheckResult(false, "API key is not configured", null);
        }
        LlmApiType apiType = providerConfig.getApiType() != null ? providerConfig.getApiType() : LlmApiType.OPENAI;
        Duration timeout = Duration.ofSeconds(providerConfig.getRequestTimeoutSeconds() != null
                ? providerConfig.getRequestTimeoutSeconds()
                : DEFAULT_TIMEOUT.toSeconds());
        String apiKey = Secret.valueOrEmpty(providerConfig.getApiKey());
        String uri = buildModelsUri(apiType, providerConfig.getBaseUrl(), apiKey);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(java.net.URI.create(uri))
                .GET()
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT);
        applyAuthHeaders(requestBuilder, apiType, apiKey);

        try {
            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return new LlmProviderCheckResult(true, "Provider responded to model listing", status);
            }
            return new LlmProviderCheckResult(false, messageForStatus(status), status);
        } catch (IOException exception) {
            log.debug("LLM provider check failed for {}: {}", providerName, exception.getMessage());
            return new LlmProviderCheckResult(false, "Provider check failed: " + exception.getMessage(), null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new LlmProviderCheckResult(false, "Provider check interrupted", null);
        } catch (IllegalArgumentException exception) {
            return new LlmProviderCheckResult(false, exception.getMessage(), null);
        }
    }

    private void applyAuthHeaders(HttpRequest.Builder requestBuilder, LlmApiType apiType, String apiKey) {
        switch (apiType) {
        case ANTHROPIC -> requestBuilder
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01");
        case GEMINI -> {
            // Gemini accepts the API key as a query parameter in the model-listing
            // endpoint.
        }
        case OPENAI -> requestBuilder.header("Authorization", "Bearer " + apiKey);
        }
    }

    private String buildModelsUri(LlmApiType apiType, String configuredBaseUrl, String apiKey) {
        return switch (apiType) {
        case ANTHROPIC -> appendPath(
                LlmEndpointAllowlist.canonicalBaseUrl(configuredBaseUrl, "https://api.anthropic.com"),
                "/v1/models");
        case GEMINI -> appendPath(
                LlmEndpointAllowlist.canonicalBaseUrl(
                        configuredBaseUrl,
                        "https://generativelanguage.googleapis.com/v1beta"),
                "/models")
                + "?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        case OPENAI -> appendPath(
                LlmEndpointAllowlist.canonicalBaseUrl(configuredBaseUrl, "https://api.openai.com/v1"),
                "/models");
        };
    }

    private String appendPath(String baseUrl, String path) {
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if ("/v1/models".equals(path) && base.endsWith("/v1")) {
            return base + "/models";
        }
        return base + path;
    }

    private String messageForStatus(int status) {
        if (status == 401 || status == 403) {
            return "Provider rejected the configured credentials";
        }
        if (status == 404) {
            return "Provider model-listing endpoint was not found";
        }
        if (status >= 500) {
            return "Provider returned a server error";
        }
        return "Provider check failed with HTTP " + status;
    }
}
