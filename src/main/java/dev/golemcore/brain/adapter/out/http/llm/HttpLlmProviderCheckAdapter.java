package dev.golemcore.brain.adapter.out.http.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.golemcore.brain.application.port.out.LlmProviderCheckPort;
import dev.golemcore.brain.domain.Secret;
import dev.golemcore.brain.domain.llm.LlmApiType;
import dev.golemcore.brain.domain.llm.LlmProviderCheckResult;
import dev.golemcore.brain.domain.llm.LlmProviderConfig;
import dev.langchain4j.model.catalog.ModelDescription;
import dev.langchain4j.model.openai.OpenAiModelCatalog;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        try {
            if (apiType == LlmApiType.OPENAI) {
                List<String> modelIds = listOpenAiModels(providerConfig, apiKey, timeout);
                return new LlmProviderCheckResult(true, modelListingMessage(modelIds), 200, modelIds);
            }

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(java.net.URI.create(uri))
                    .GET()
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT);
            applyAuthHeaders(requestBuilder, apiType, apiKey);

            HttpResponse<String> response = httpClient.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                List<String> modelIds = extractModelIds(apiType, response.body());
                return new LlmProviderCheckResult(true, modelListingMessage(modelIds), status, modelIds);
            }
            return new LlmProviderCheckResult(false, messageForStatus(status), status);
        } catch (IOException exception) {
            String message = networkFailureMessage(exception);
            log.debug("LLM provider check failed for {}: {}", providerName, message);
            return new LlmProviderCheckResult(false, message, null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new LlmProviderCheckResult(false, "Provider check interrupted", null);
        } catch (RuntimeException exception) {
            String message = runtimeFailureMessage(exception);
            log.debug("LLM provider check failed for {}: {}", providerName, message);
            return new LlmProviderCheckResult(false, message, null);
        }
    }

    private List<String> listOpenAiModels(LlmProviderConfig providerConfig, String apiKey, Duration timeout) {
        return OpenAiModelCatalog.builder()
                .apiKey(apiKey)
                .baseUrl(LlmEndpointResolver.canonicalBaseUrl(providerConfig.getBaseUrl(), "https://api.openai.com/v1"))
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .userAgent(USER_AGENT)
                .build()
                .listModels()
                .stream()
                .map(ModelDescription::name)
                .filter(modelId -> modelId != null && !modelId.isBlank())
                .distinct()
                .toList();
    }

    static String networkFailureMessage(IOException exception) {
        String detail = firstReadableDetail(exception.getMessage());
        if (detail == null && exception.getCause() != null) {
            detail = firstReadableDetail(exception.getCause().getMessage());
        }
        if (detail == null) {
            return "Provider check failed: could not reach the provider endpoint";
        }
        return "Provider check failed: " + detail;
    }

    static String runtimeFailureMessage(RuntimeException exception) {
        String detail = firstReadableDetail(exception.getMessage());
        if (detail == null && exception.getCause() != null) {
            detail = firstReadableDetail(exception.getCause().getMessage());
        }
        if (detail == null) {
            return "Provider check failed: could not reach the provider endpoint";
        }
        return "Provider check failed: " + detail;
    }

    private List<String> extractModelIds(LlmApiType apiType, String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode listing = apiType == LlmApiType.GEMINI ? root.path("models") : root.path("data");
            if (!listing.isArray()) {
                return List.of();
            }
            Set<String> modelIds = new LinkedHashSet<>();
            for (JsonNode modelNode : listing) {
                String modelId = extractModelId(apiType, modelNode);
                if (modelId != null) {
                    modelIds.add(modelId);
                }
            }
            return List.copyOf(modelIds);
        } catch (IOException exception) {
            log.debug("Failed to parse LLM provider model listing: {}", exception.getMessage());
            return List.of();
        }
    }

    private String extractModelId(LlmApiType apiType, JsonNode modelNode) {
        String id = textValue(modelNode.path("id"));
        if (id != null || apiType != LlmApiType.GEMINI) {
            return id;
        }
        String name = textValue(modelNode.path("name"));
        if (name == null) {
            return null;
        }
        return name.startsWith("models/") ? name.substring("models/".length()) : name;
    }

    private String textValue(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }

    private String modelListingMessage(List<String> modelIds) {
        if (modelIds.isEmpty()) {
            return "Provider responded to model listing";
        }
        return "Provider responded to model listing (" + modelIds.size() + " models)";
    }

    private static String firstReadableDetail(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
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
                LlmEndpointResolver.canonicalBaseUrl(configuredBaseUrl, "https://api.anthropic.com"),
                "/v1/models");
        case GEMINI -> appendPath(
                LlmEndpointResolver.canonicalBaseUrl(
                        configuredBaseUrl,
                        "https://generativelanguage.googleapis.com/v1beta"),
                "/models")
                + "?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        case OPENAI -> appendPath(
                LlmEndpointResolver.canonicalBaseUrl(configuredBaseUrl, "https://api.openai.com/v1"),
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
