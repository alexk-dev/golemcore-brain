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
import dev.golemcore.brain.domain.llm.LlmProviderConfig;
import dev.golemcore.brain.domain.llm.LlmToolCall;
import dev.golemcore.brain.domain.llm.LlmToolDefinition;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiResponsesStreamingChatModel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class Langchain4jLlmAdapter implements LlmChatPort, LlmEmbeddingPort {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(300);
    private static final int ANTHROPIC_THINKING_RESPONSE_TOKEN_BUFFER = 1024;
    private static final String SCHEMA_KEY_PROPERTIES = "properties";

    private final ObjectMapper objectMapper;
    private final com.fasterxml.jackson.databind.ObjectMapper compatibilityObjectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @Override
    public LlmEmbeddingResponse embed(LlmEmbeddingRequest request) {
        if (request.getProvider() == null || request.getModel() == null) {
            throw new IllegalArgumentException("LLM provider and embedding model are required");
        }
        LlmApiType apiType = request.getProvider().getApiType() != null
                ? request.getProvider().getApiType()
                : LlmApiType.OPENAI;
        if (apiType == LlmApiType.ANTHROPIC) {
            throw new IllegalArgumentException("Embedding indexing is not supported by Anthropic chat APIs");
        }

        List<String> inputs = request.getInputs() != null ? request.getInputs() : List.of();
        if (inputs.isEmpty()) {
            return LlmEmbeddingResponse.builder().embeddings(List.of()).build();
        }
        List<Embedding> embeddings = switch (apiType) {
        case GEMINI -> createGeminiEmbeddingModel(request).embedAll(inputs.stream().map(TextSegment::from).toList())
                .content();
        case OPENAI -> createOpenAiEmbeddingModel(request).embedAll(inputs.stream().map(TextSegment::from).toList())
                .content();
        case ANTHROPIC -> throw new IllegalArgumentException(
                "Embedding indexing is not supported by Anthropic chat APIs");
        };
        return LlmEmbeddingResponse.builder()
                .embeddings(embeddings.stream()
                        .map(embedding -> embedding.vectorAsList().stream()
                                .map(Float::doubleValue)
                                .toList())
                        .toList())
                .build();
    }

    @Override
    public LlmChatResponse chat(LlmChatRequest request) {
        if (request.getProvider() == null || request.getModel() == null) {
            throw new IllegalArgumentException("LLM provider and model are required");
        }
        LlmApiType apiType = request.getProvider().getApiType() != null
                ? request.getProvider().getApiType()
                : LlmApiType.OPENAI;
        ChatRequest chatRequest = toLangchainChatRequest(request);
        ChatResponse response = switch (apiType) {
        case ANTHROPIC -> createAnthropicChatModel(request).chat(chatRequest);
        case GEMINI -> createGeminiChatModel(request).chat(chatRequest);
        case OPENAI -> Boolean.TRUE.equals(request.getProvider().getLegacyApi())
                ? createLegacyChatModel(request).chat(chatRequest)
                : chatViaResponsesApi(createResponsesStreamingModel(request), chatRequest);
        };
        return toLlmChatResponse(response);
    }

    private ChatModel createLegacyChatModel(LlmChatRequest request) {
        LlmProviderConfig provider = request.getProvider();
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(Secret.valueOrEmpty(provider.getApiKey()))
                .modelName(request.getModel().getModelId())
                .baseUrl(openAiBaseUrl(provider))
                .timeout(requestTimeout(provider))
                .maxRetries(0);
        applyReasoning(builder, request);
        if (supportsTemperature(request) && request.getModel().getTemperature() != null) {
            builder.temperature(request.getModel().getTemperature());
        }
        return builder.build();
    }

    private StreamingChatModel createResponsesStreamingModel(LlmChatRequest request) {
        LlmProviderConfig provider = request.getProvider();
        Duration timeout = requestTimeout(provider);
        OpenAiResponsesStreamingChatModel.Builder builder = OpenAiResponsesStreamingChatModel.builder()
                .apiKey(Secret.valueOrEmpty(provider.getApiKey()))
                .modelName(request.getModel().getModelId())
                .baseUrl(openAiBaseUrl(provider))
                .httpClientBuilder(createResponsesCompatibilityHttpClientBuilder(timeout))
                .logRequests(false)
                .logResponses(false);
        if (hasReasoningEffort(request)) {
            builder.reasoningEffort(request.getModel().getReasoningEffort().trim());
        }
        if (supportsTemperature(request) && request.getModel().getTemperature() != null) {
            builder.temperature(request.getModel().getTemperature());
        }
        return builder.build();
    }

    private ChatModel createAnthropicChatModel(LlmChatRequest request) {
        LlmProviderConfig provider = request.getProvider();
        AnthropicChatModel.AnthropicChatModelBuilder builder = AnthropicChatModel.builder()
                .apiKey(Secret.valueOrEmpty(provider.getApiKey()))
                .modelName(request.getModel().getModelId())
                .baseUrl(anthropicBaseUrl(provider))
                .timeout(requestTimeout(provider))
                .maxRetries(0)
                .logRequests(false)
                .logResponses(false);
        if (supportsTemperature(request) && request.getModel().getTemperature() != null) {
            builder.temperature(request.getModel().getTemperature());
        }
        applyAnthropicReasoning(builder, request);
        return builder.build();
    }

    private ChatModel createGeminiChatModel(LlmChatRequest request) {
        LlmProviderConfig provider = request.getProvider();
        GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder builder = GoogleAiGeminiChatModel.builder()
                .apiKey(Secret.valueOrEmpty(provider.getApiKey()))
                .modelName(request.getModel().getModelId())
                .baseUrl(geminiBaseUrl(provider))
                .timeout(requestTimeout(provider))
                .maxRetries(0)
                .logRequests(false)
                .logResponses(false);
        if (supportsTemperature(request) && request.getModel().getTemperature() != null) {
            builder.temperature(request.getModel().getTemperature());
        }
        applyGeminiReasoning(builder, request);
        return builder.build();
    }

    private OpenAiEmbeddingModel createOpenAiEmbeddingModel(LlmEmbeddingRequest request) {
        OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder builder = OpenAiEmbeddingModel.builder()
                .apiKey(Secret.valueOrEmpty(request.getProvider().getApiKey()))
                .modelName(request.getModel().getModelId())
                .baseUrl(openAiBaseUrl(request.getProvider()))
                .timeout(requestTimeout(request.getProvider()))
                .maxRetries(0)
                .logRequests(false)
                .logResponses(false);
        if (request.getModel().getDimensions() != null) {
            builder.dimensions(request.getModel().getDimensions());
        }
        return builder.build();
    }

    private GoogleAiEmbeddingModel createGeminiEmbeddingModel(LlmEmbeddingRequest request) {
        GoogleAiEmbeddingModel.GoogleAiEmbeddingModelBuilder builder = GoogleAiEmbeddingModel.builder()
                .apiKey(Secret.valueOrEmpty(request.getProvider().getApiKey()))
                .modelName(request.getModel().getModelId())
                .baseUrl(geminiBaseUrl(request.getProvider()))
                .timeout(requestTimeout(request.getProvider()))
                .maxRetries(0)
                .logRequests(false)
                .logResponses(false);
        if (request.getModel().getDimensions() != null) {
            builder.outputDimensionality(request.getModel().getDimensions());
        }
        return builder.build();
    }

    private HttpClientBuilder createResponsesCompatibilityHttpClientBuilder(Duration timeout) {
        JdkHttpClientBuilder baseBuilder = new JdkHttpClientBuilder();
        baseBuilder.connectTimeout(timeout);
        baseBuilder.readTimeout(timeout);
        return new ResponsesCompatibilityHttpClientBuilder(baseBuilder, compatibilityObjectMapper);
    }

    private ChatResponse chatViaResponsesApi(StreamingChatModel model, ChatRequest request) {
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        model.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onCompleteResponse(ChatResponse chatResponse) {
                future.complete(chatResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });
        try {
            return future.join();
        } catch (CompletionException exception) {
            throw new IllegalStateException("LLM chat request failed: " + exception.getMessage(),
                    exception.getCause() != null ? exception.getCause() : exception);
        }
    }

    private ChatRequest toLangchainChatRequest(LlmChatRequest request) {
        ChatRequest.Builder builder = ChatRequest.builder()
                .messages(toLangchainMessages(request));
        if (supportsTemperature(request) && request.getModel().getTemperature() != null) {
            builder.temperature(request.getModel().getTemperature());
        }
        List<ToolSpecification> tools = toLangchainTools(request);
        if (!tools.isEmpty()) {
            builder.toolSpecifications(tools);
        }
        return builder.build();
    }

    private List<ChatMessage> toLangchainMessages(LlmChatRequest request) {
        List<ChatMessage> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            messages.add(SystemMessage.from(request.getSystemPrompt()));
        }
        for (LlmChatMessage message : request.getMessages()) {
            if (message == null || message.getRole() == null) {
                continue;
            }
            switch (message.getRole()) {
            case "assistant" -> messages.add(toAiMessage(message));
            case "system" -> messages.add(SystemMessage.from(nonNullText(message.getContent())));
            case "tool" -> messages.add(ToolExecutionResultMessage.from(
                    nonNullText(message.getToolCallId()),
                    nonNullText(message.getToolName()),
                    nonNullText(message.getContent())));
            case "user" -> messages.add(UserMessage.from(nonNullText(message.getContent())));
            default -> {
                log.warn("[LLM] Unknown chat message role '{}', treating it as user", message.getRole());
                messages.add(UserMessage.from(nonNullText(message.getContent())));
            }
            }
        }
        return messages;
    }

    private AiMessage toAiMessage(LlmChatMessage message) {
        if (!message.hasToolCalls()) {
            return AiMessage.from(nonNullText(message.getContent()));
        }
        AiMessage.Builder builder = AiMessage.builder()
                .toolExecutionRequests(message.getToolCalls().stream()
                        .map(this::toToolExecutionRequest)
                        .toList());
        if (message.getContent() != null && !message.getContent().isBlank()) {
            builder.text(message.getContent());
        }
        return builder.build();
    }

    private ToolExecutionRequest toToolExecutionRequest(LlmToolCall toolCall) {
        return ToolExecutionRequest.builder()
                .id(toolCall.getId())
                .name(toolCall.getName())
                .arguments(writeJson(toolCall.getArguments() != null ? toolCall.getArguments() : Map.of()))
                .build();
    }

    private List<ToolSpecification> toLangchainTools(LlmChatRequest request) {
        if (request.getTools() == null || request.getTools().isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, LlmToolDefinition> uniqueTools = new LinkedHashMap<>();
        for (LlmToolDefinition tool : request.getTools()) {
            if (tool == null || tool.getName() == null || tool.getName().isBlank()) {
                log.warn("[LLM] Dropping tool with blank name before request serialization");
                continue;
            }
            if (uniqueTools.putIfAbsent(tool.getName(), tool) != null) {
                log.warn("[LLM] Dropping duplicate tool definition '{}' before request serialization", tool.getName());
            }
        }
        List<ToolSpecification> tools = new ArrayList<>(uniqueTools.size());
        for (LlmToolDefinition tool : uniqueTools.values()) {
            tools.add(toToolSpecification(tool));
        }
        return tools;
    }

    private ToolSpecification toToolSpecification(LlmToolDefinition tool) {
        ToolSpecification.Builder builder = ToolSpecification.builder()
                .name(tool.getName())
                .description(tool.getDescription());
        JsonObjectSchema parameters = toToolParameters(tool);
        if (parameters != null) {
            builder.parameters(parameters);
        }
        return builder.build();
    }

    private JsonObjectSchema toToolParameters(LlmToolDefinition tool) {
        if (tool.getInputSchema() == null) {
            return null;
        }
        Map<String, Object> properties = stringObjectMap(tool.getInputSchema().get(SCHEMA_KEY_PROPERTIES),
                tool.getName(), SCHEMA_KEY_PROPERTIES);
        if (properties == null) {
            return null;
        }
        JsonObjectSchema.Builder schemaBuilder = JsonObjectSchema.builder();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Map<String, Object> paramSchema = stringObjectMap(entry.getValue(), tool.getName(),
                    SCHEMA_KEY_PROPERTIES + "." + entry.getKey());
            if (paramSchema == null) {
                continue;
            }
            schemaBuilder.addProperty(entry.getKey(), toJsonSchemaElement(tool.getName(),
                    SCHEMA_KEY_PROPERTIES + "." + entry.getKey(), paramSchema));
        }
        List<String> required = stringList(tool.getInputSchema().get("required"), tool.getName(), "required");
        if (required != null && !required.isEmpty()) {
            schemaBuilder.required(required);
        }
        return schemaBuilder.build();
    }

    private JsonSchemaElement toJsonSchemaElement(String toolName, String path, Map<String, Object> paramSchema) {
        String type = stringValue(paramSchema.get("type"), toolName, path + ".type");
        String description = stringValue(paramSchema.get("description"), toolName, path + ".description");
        List<String> enumValues = stringList(paramSchema.get("enum"), toolName, path + ".enum");
        if (enumValues != null && !enumValues.isEmpty()) {
            JsonEnumSchema.Builder builder = JsonEnumSchema.builder().enumValues(enumValues);
            if (description != null && !description.isBlank()) {
                builder.description(description);
            }
            return builder.build();
        }
        if (type == null) {
            type = "string";
        }
        return switch (type) {
        case "array" -> arraySchema(toolName, path, paramSchema, description);
        case "boolean" -> booleanSchema(description);
        case "integer" -> integerSchema(description);
        case "number" -> numberSchema(description);
        case "object" -> objectSchema(toolName, path, paramSchema, description);
        case "string" -> stringSchema(description);
        default -> stringSchema(description);
        };
    }

    private JsonArraySchema arraySchema(String toolName, String path, Map<String, Object> paramSchema,
            String description) {
        JsonArraySchema.Builder builder = JsonArraySchema.builder();
        if (description != null && !description.isBlank()) {
            builder.description(description);
        }
        Map<String, Object> items = stringObjectMap(paramSchema.get("items"), toolName, path + ".items");
        if (items != null) {
            builder.items(toJsonSchemaElement(toolName, path + ".items", items));
        }
        return builder.build();
    }

    private JsonBooleanSchema booleanSchema(String description) {
        JsonBooleanSchema.Builder builder = JsonBooleanSchema.builder();
        if (description != null && !description.isBlank()) {
            builder.description(description);
        }
        return builder.build();
    }

    private JsonIntegerSchema integerSchema(String description) {
        JsonIntegerSchema.Builder builder = JsonIntegerSchema.builder();
        if (description != null && !description.isBlank()) {
            builder.description(description);
        }
        return builder.build();
    }

    private JsonNumberSchema numberSchema(String description) {
        JsonNumberSchema.Builder builder = JsonNumberSchema.builder();
        if (description != null && !description.isBlank()) {
            builder.description(description);
        }
        return builder.build();
    }

    private JsonObjectSchema objectSchema(String toolName, String path, Map<String, Object> paramSchema,
            String description) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        if (description != null && !description.isBlank()) {
            builder.description(description);
        }
        Map<String, Object> properties = stringObjectMap(paramSchema.get(SCHEMA_KEY_PROPERTIES), toolName,
                path + "." + SCHEMA_KEY_PROPERTIES);
        if (properties != null) {
            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                Map<String, Object> nestedSchema = stringObjectMap(entry.getValue(), toolName,
                        path + "." + SCHEMA_KEY_PROPERTIES + "." + entry.getKey());
                if (nestedSchema != null) {
                    builder.addProperty(entry.getKey(), toJsonSchemaElement(toolName,
                            path + "." + SCHEMA_KEY_PROPERTIES + "." + entry.getKey(), nestedSchema));
                }
            }
        }
        return builder.build();
    }

    private JsonStringSchema stringSchema(String description) {
        JsonStringSchema.Builder builder = JsonStringSchema.builder();
        if (description != null && !description.isBlank()) {
            builder.description(description);
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private LlmChatResponse toLlmChatResponse(ChatResponse response) {
        AiMessage aiMessage = response.aiMessage();
        List<LlmToolCall> toolCalls = aiMessage.hasToolExecutionRequests()
                ? aiMessage.toolExecutionRequests().stream()
                        .map(toolCall -> LlmToolCall.builder()
                                .id(toolCall.id())
                                .name(toolCall.name())
                                .arguments(parseArguments(toolCall.arguments()))
                                .build())
                        .toList()
                : List.of();
        return LlmChatResponse.builder()
                .content(aiMessage.text())
                .toolCalls(toolCalls)
                .finishReason(response.finishReason() != null
                        ? response.finishReason().name().toLowerCase(Locale.ROOT)
                        : null)
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(rawArguments, Map.class);
    }

    private Map<String, Object> stringObjectMap(Object rawValue, String toolName, String path) {
        if (!(rawValue instanceof Map<?, ?> rawMap)) {
            if (rawValue != null) {
                log.warn("[LLM] Invalid schema object for tool '{}' at {}: {}", toolName, path,
                        rawValue.getClass().getSimpleName());
            }
            return null;
        }
        Map<String, Object> casted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() instanceof String key) {
                casted.put(key, entry.getValue());
            } else {
                log.warn("[LLM] Dropping non-string schema key for tool '{}' at {}", toolName, path);
            }
        }
        return casted;
    }

    private List<String> stringList(Object rawValue, String toolName, String path) {
        if (!(rawValue instanceof List<?> rawList)) {
            if (rawValue != null) {
                log.warn("[LLM] Invalid schema list for tool '{}' at {}: {}", toolName, path,
                        rawValue.getClass().getSimpleName());
            }
            return null;
        }
        List<String> values = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            if (item instanceof String stringValue && !stringValue.isBlank()) {
                values.add(stringValue);
            } else {
                log.warn("[LLM] Dropping non-string schema list item for tool '{}' at {}", toolName, path);
            }
        }
        return values;
    }

    private String stringValue(Object rawValue, String toolName, String path) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof String stringValue) {
            return stringValue;
        }
        log.warn("[LLM] Invalid schema string for tool '{}' at {}: {}", toolName, path,
                rawValue.getClass().getSimpleName());
        return null;
    }

    private void applyReasoning(OpenAiChatModel.OpenAiChatModelBuilder builder, LlmChatRequest request) {
        if (hasReasoningEffort(request)) {
            builder.reasoningEffort(request.getModel().getReasoningEffort().trim());
        }
    }

    private void applyAnthropicReasoning(AnthropicChatModel.AnthropicChatModelBuilder builder, LlmChatRequest request) {
        if (!hasReasoningEffort(request)) {
            return;
        }
        Integer thinkingBudget = anthropicThinkingBudget(request.getModel().getReasoningEffort().trim());
        if (thinkingBudget == null) {
            return;
        }
        builder.thinkingType("enabled");
        builder.thinkingBudgetTokens(thinkingBudget);
        builder.maxTokens(thinkingBudget + ANTHROPIC_THINKING_RESPONSE_TOKEN_BUFFER);
    }

    private Integer anthropicThinkingBudget(String reasoningEffort) {
        return switch (reasoningEffort.toLowerCase(Locale.ROOT)) {
        case "minimal" -> 1024;
        case "low" -> 2048;
        case "medium" -> 8192;
        case "high" -> 24576;
        case "xhigh" -> 32768;
        default -> null;
        };
    }

    private void applyGeminiReasoning(
            GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder builder,
            LlmChatRequest request) {
        if (!hasReasoningEffort(request)) {
            return;
        }
        String reasoningEffort = request.getModel().getReasoningEffort().trim();
        GeminiThinkingConfig.Builder thinkingBuilder = GeminiThinkingConfig.builder();
        Integer thinkingBudget = geminiThinkingBudget(reasoningEffort);
        if (thinkingBudget != null) {
            thinkingBuilder.thinkingBudget(thinkingBudget);
        } else {
            thinkingBuilder.thinkingLevel(reasoningEffort);
        }
        builder.thinkingConfig(thinkingBuilder.build());
    }

    private Integer geminiThinkingBudget(String reasoningEffort) {
        return switch (reasoningEffort.toLowerCase(Locale.ROOT)) {
        case "minimal" -> 512;
        case "low" -> 1024;
        case "medium" -> 8192;
        case "high" -> 24576;
        case "xhigh" -> 32768;
        default -> null;
        };
    }

    private boolean hasReasoningEffort(LlmChatRequest request) {
        String reasoningEffort = request.getModel().getReasoningEffort();
        return reasoningEffort != null && !reasoningEffort.isBlank() && !"none".equalsIgnoreCase(reasoningEffort);
    }

    private boolean supportsTemperature(LlmChatRequest request) {
        return !Boolean.FALSE.equals(request.getModel().getSupportsTemperature());
    }

    private Duration requestTimeout(LlmProviderConfig provider) {
        return Duration.ofSeconds(provider.getRequestTimeoutSeconds() != null
                ? provider.getRequestTimeoutSeconds()
                : DEFAULT_TIMEOUT.toSeconds());
    }

    private String openAiBaseUrl(LlmProviderConfig provider) {
        return LlmEndpointResolver.canonicalBaseUrl(provider.getBaseUrl(), "https://api.openai.com/v1");
    }

    private String anthropicBaseUrl(LlmProviderConfig provider) {
        return LlmEndpointResolver.canonicalBaseUrl(provider.getBaseUrl(), "https://api.anthropic.com/v1");
    }

    private String geminiBaseUrl(LlmProviderConfig provider) {
        return LlmEndpointResolver.canonicalBaseUrl(provider.getBaseUrl(),
                "https://generativelanguage.googleapis.com/v1beta");
    }

    private String nonNullText(String text) {
        return text != null ? text : "";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to serialize LLM request", exception);
        }
    }

}
