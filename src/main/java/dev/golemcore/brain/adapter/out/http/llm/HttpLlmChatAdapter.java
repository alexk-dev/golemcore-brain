package dev.golemcore.brain.adapter.out.http.llm;

import dev.golemcore.brain.application.port.out.LlmChatPort;
import dev.golemcore.brain.application.port.out.LlmEmbeddingPort;
import dev.golemcore.brain.domain.Secret;
import dev.golemcore.brain.domain.llm.LlmApiType;
import dev.golemcore.brain.domain.llm.LlmChatMessage;
import dev.golemcore.brain.domain.llm.LlmChatRequest;
import dev.golemcore.brain.domain.llm.LlmChatResponse;
import dev.golemcore.brain.domain.llm.LlmEmbeddingRequest;
import dev.golemcore.brain.domain.llm.LlmEmbeddingResponse;
import dev.golemcore.brain.domain.llm.LlmToolCall;
import dev.golemcore.brain.domain.llm.LlmToolDefinition;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class HttpLlmChatAdapter implements LlmChatPort, LlmEmbeddingPort {

    private static final String USER_AGENT = "golemcore-brain-dynamic-api";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(300);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public LlmEmbeddingResponse embed(LlmEmbeddingRequest request) {
        if (request.getProvider() == null || request.getModel() == null) {
            throw new IllegalArgumentException("LLM provider and embedding model are required");
        }
        LlmApiType apiType = request.getProvider().getApiType() != null
                ? request.getProvider().getApiType()
                : LlmApiType.OPENAI;
        if (apiType != LlmApiType.OPENAI) {
            throw new IllegalArgumentException("Embedding indexing currently supports OpenAI-compatible APIs");
        }

        String apiKey = Secret.valueOrEmpty(request.getProvider().getApiKey());
        Duration timeout = Duration.ofSeconds(request.getProvider().getRequestTimeoutSeconds() != null
                ? request.getProvider().getRequestTimeoutSeconds()
                : DEFAULT_TIMEOUT.toSeconds());
        String uri = appendPath(LlmEndpointAllowlist.canonicalBaseUrl(request.getProvider().getBaseUrl(),
                "https://api.openai.com/v1"), "/embeddings");
        String requestBody = writeJson(toOpenAiEmbeddingRequestBody(request));

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(uri))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("LLM embedding request failed with HTTP " + response.statusCode());
            }
            return parseOpenAiEmbeddingResponse(response.body());
        } catch (IOException exception) {
            log.debug("LLM embedding request failed: {}", exception.getMessage());
            throw new IllegalStateException("LLM embedding request failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM embedding request interrupted", exception);
        }
    }

    @Override
    public LlmChatResponse chat(LlmChatRequest request) {
        if (request.getProvider() == null || request.getModel() == null) {
            throw new IllegalArgumentException("LLM provider and model are required");
        }
        LlmApiType apiType = request.getProvider().getApiType() != null
                ? request.getProvider().getApiType()
                : LlmApiType.OPENAI;
        if (apiType != LlmApiType.OPENAI) {
            throw new IllegalArgumentException("Dynamic API agent loop currently supports OpenAI-compatible chat APIs");
        }

        String apiKey = Secret.valueOrEmpty(request.getProvider().getApiKey());
        Duration timeout = Duration.ofSeconds(request.getProvider().getRequestTimeoutSeconds() != null
                ? request.getProvider().getRequestTimeoutSeconds()
                : DEFAULT_TIMEOUT.toSeconds());
        String uri = appendPath(LlmEndpointAllowlist.canonicalBaseUrl(request.getProvider().getBaseUrl(),
                "https://api.openai.com/v1"), "/chat/completions");
        String requestBody = writeJson(toOpenAiRequestBody(request));

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(uri))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .build();
        try {
            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("LLM chat request failed with HTTP " + response.statusCode());
            }
            return parseOpenAiResponse(response.body());
        } catch (IOException exception) {
            log.debug("LLM chat request failed: {}", exception.getMessage());
            throw new IllegalStateException("LLM chat request failed: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM chat request interrupted", exception);
        }
    }

    private Map<String, Object> toOpenAiEmbeddingRequestBody(LlmEmbeddingRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel().getModelId());
        body.put("input", request.getInputs() != null ? request.getInputs() : List.of());
        if (request.getModel().getDimensions() != null) {
            body.put("dimensions", request.getModel().getDimensions());
        }
        return body;
    }

    private Map<String, Object> toOpenAiRequestBody(LlmChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.getModel().getModelId());
        body.put("messages", toOpenAiMessages(request));
        if (request.getModel().getReasoningEffort() != null) {
            body.put("reasoning_effort", request.getModel().getReasoningEffort().getValue());
        } else if (request.getModel().getTemperature() != null) {
            body.put("temperature", request.getModel().getTemperature());
        }
        if (request.getTools() != null && !request.getTools().isEmpty()) {
            body.put("tools", request.getTools().stream().map(this::toOpenAiTool).toList());
            body.put("tool_choice", "auto");
        }
        return body;
    }

    private List<Map<String, Object>> toOpenAiMessages(LlmChatRequest request) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
        }
        for (LlmChatMessage message : request.getMessages()) {
            Map<String, Object> mapped = new LinkedHashMap<>();
            mapped.put("role", message.getRole());
            if ("tool".equals(message.getRole())) {
                mapped.put("tool_call_id", message.getToolCallId());
                mapped.put("content", message.getContent() != null ? message.getContent() : "");
            } else {
                if (message.getContent() != null) {
                    mapped.put("content", message.getContent());
                }
                if (message.hasToolCalls()) {
                    mapped.put("tool_calls", message.getToolCalls().stream().map(this::toOpenAiToolCall).toList());
                }
                if (!mapped.containsKey("content") && !mapped.containsKey("tool_calls")) {
                    mapped.put("content", "");
                }
            }
            messages.add(mapped);
        }
        return messages;
    }

    private Map<String, Object> toOpenAiTool(LlmToolDefinition tool) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", tool.getName(),
                        "description", tool.getDescription(),
                        "parameters", tool.getInputSchema()));
    }

    private Map<String, Object> toOpenAiToolCall(LlmToolCall toolCall) {
        return Map.of(
                "id", toolCall.getId(),
                "type", "function",
                "function", Map.of(
                        "name", toolCall.getName(),
                        "arguments", writeJson(toolCall.getArguments() != null ? toolCall.getArguments() : Map.of())));
    }

    private LlmEmbeddingResponse parseOpenAiEmbeddingResponse(String responseBody) {
        Map<String, Object> body = objectMapper.readValue(responseBody, Map.class);
        List<Object> data = asList(body.get("data"));
        List<List<Double>> embeddings = new ArrayList<>();
        for (Object rawItem : data) {
            Map<String, Object> item = asMap(rawItem);
            embeddings.add(asDoubleList(asList(item.get("embedding"))));
        }
        return LlmEmbeddingResponse.builder()
                .embeddings(embeddings)
                .build();
    }

    @SuppressWarnings("unchecked")
    private LlmChatResponse parseOpenAiResponse(String responseBody) {
        Map<String, Object> body = objectMapper.readValue(responseBody, Map.class);
        List<Object> choices = asList(body.get("choices"));
        if (choices.isEmpty()) {
            throw new IllegalStateException("LLM chat response did not contain choices");
        }
        Map<String, Object> choice = asMap(choices.getFirst());
        Map<String, Object> message = asMap(choice.get("message"));
        return LlmChatResponse.builder()
                .content(asString(message.get("content")))
                .toolCalls(parseToolCalls(asList(message.get("tool_calls"))))
                .finishReason(asString(choice.get("finish_reason")))
                .build();
    }

    private List<LlmToolCall> parseToolCalls(List<Object> toolCalls) {
        if (toolCalls.isEmpty()) {
            return List.of();
        }
        List<LlmToolCall> parsed = new ArrayList<>();
        for (Object rawToolCall : toolCalls) {
            Map<String, Object> toolCall = asMap(rawToolCall);
            Map<String, Object> function = asMap(toolCall.get("function"));
            parsed.add(LlmToolCall.builder()
                    .id(asString(toolCall.get("id")))
                    .name(asString(function.get("name")))
                    .arguments(parseArguments(function.get("arguments")))
                    .build());
        }
        return parsed;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(Object rawArguments) {
        if (rawArguments instanceof Map<?, ?> arguments) {
            return new LinkedHashMap<>((Map<String, Object>) arguments);
        }
        if (rawArguments instanceof String text && !text.isBlank()) {
            return objectMapper.readValue(text, Map.class);
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private List<Object> asList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    private String asString(Object value) {
        return value instanceof String text ? text : null;
    }

    private List<Double> asDoubleList(List<Object> values) {
        List<Double> doubles = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof Number number) {
                doubles.add(number.doubleValue());
            }
        }
        return doubles;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to serialize LLM request", exception);
        }
    }

    private String appendPath(String baseUrl, String path) {
        String base = baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }
}
