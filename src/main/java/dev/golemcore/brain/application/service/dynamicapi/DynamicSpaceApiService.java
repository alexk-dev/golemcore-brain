package dev.golemcore.brain.application.service.dynamicapi;

import dev.golemcore.brain.application.exception.WikiNotFoundException;
import dev.golemcore.brain.application.port.out.DynamicSpaceApiRepository;
import dev.golemcore.brain.application.port.out.DynamicSpaceApiToolPort;
import dev.golemcore.brain.application.port.out.JsonCodecPort;
import dev.golemcore.brain.application.port.out.LlmChatPort;
import dev.golemcore.brain.application.port.out.LlmSettingsRepository;
import dev.golemcore.brain.application.port.out.SpaceRepository;
import dev.golemcore.brain.application.service.auth.AuthAccessDeniedException;
import dev.golemcore.brain.application.service.auth.AuthUnauthorizedException;
import dev.golemcore.brain.domain.Secret;
import dev.golemcore.brain.domain.auth.AuthContext;
import dev.golemcore.brain.domain.auth.UserRole;
import dev.golemcore.brain.domain.dynamicapi.DynamicSpaceApiConfig;
import dev.golemcore.brain.domain.dynamicapi.DynamicSpaceApiRunResult;
import dev.golemcore.brain.domain.dynamicapi.DynamicSpaceApiSettings;
import dev.golemcore.brain.domain.llm.LlmChatMessage;
import dev.golemcore.brain.domain.llm.LlmChatRequest;
import dev.golemcore.brain.domain.llm.LlmChatResponse;
import dev.golemcore.brain.domain.llm.LlmModelConfig;
import dev.golemcore.brain.domain.llm.LlmModelKind;
import dev.golemcore.brain.domain.llm.LlmProviderConfig;
import dev.golemcore.brain.domain.llm.LlmSettings;
import dev.golemcore.brain.domain.llm.LlmToolCall;
import dev.golemcore.brain.domain.llm.LlmToolResult;
import dev.golemcore.brain.domain.space.Space;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@RequiredArgsConstructor
public class DynamicSpaceApiService {

    private static final Pattern API_SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,80}$");
    private static final int DEFAULT_MAX_ITERATIONS = 6;
    private static final int MAX_ITERATIONS = 20;
    private static final String FINAL_JSON_INSTRUCTION = """
            Return the final response as valid JSON only. Use filesystem tools when the request needs wiki content.
            Do not wrap the JSON in Markdown fences.
            """;

    private final SpaceRepository spaceRepository;
    private final DynamicSpaceApiRepository dynamicSpaceApiRepository;
    private final LlmSettingsRepository llmSettingsRepository;
    private final LlmChatPort llmChatPort;
    private final DynamicSpaceApiToolPort dynamicSpaceApiToolPort;
    private final JsonCodecPort jsonCodecPort;

    public List<DynamicSpaceApiConfig> listApis(AuthContext authContext, String spaceSlug) {
        Space space = requireSpaceAccess(authContext, spaceSlug, UserRole.ADMIN);
        return normalizeSettings(dynamicSpaceApiRepository.load(space.getId())).getApis().stream()
                .sorted(Comparator.comparing(DynamicSpaceApiConfig::getSlug, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public DynamicSpaceApiConfig createApi(AuthContext authContext, String spaceSlug, SaveCommand command) {
        Space space = requireSpaceAccess(authContext, spaceSlug, UserRole.ADMIN);
        DynamicSpaceApiSettings settings = normalizeSettings(dynamicSpaceApiRepository.load(space.getId()));
        String slug = normalizeSlug(command.getSlug());
        ensureUniqueSlug(settings, null, slug);
        DynamicSpaceApiConfig api = normalizeApi(command, null, slug);
        settings.getApis().add(api);
        dynamicSpaceApiRepository.save(space.getId(), settings);
        return api;
    }

    public DynamicSpaceApiConfig updateApi(AuthContext authContext, String spaceSlug, String apiId,
            SaveCommand command) {
        Space space = requireSpaceAccess(authContext, spaceSlug, UserRole.ADMIN);
        DynamicSpaceApiSettings settings = normalizeSettings(dynamicSpaceApiRepository.load(space.getId()));
        DynamicSpaceApiConfig existing = findById(settings, apiId);
        String slug = normalizeSlug(command.getSlug());
        ensureUniqueSlug(settings, existing.getId(), slug);
        DynamicSpaceApiConfig updated = normalizeApi(command, existing, slug);
        settings.getApis().removeIf(api -> existing.getId().equals(api.getId()));
        settings.getApis().add(updated);
        dynamicSpaceApiRepository.save(space.getId(), settings);
        return updated;
    }

    public void deleteApi(AuthContext authContext, String spaceSlug, String apiId) {
        Space space = requireSpaceAccess(authContext, spaceSlug, UserRole.ADMIN);
        DynamicSpaceApiSettings settings = normalizeSettings(dynamicSpaceApiRepository.load(space.getId()));
        DynamicSpaceApiConfig existing = findById(settings, apiId);
        settings.getApis().removeIf(api -> existing.getId().equals(api.getId()));
        dynamicSpaceApiRepository.save(space.getId(), settings);
    }

    public DynamicSpaceApiRunResult runApi(AuthContext authContext, String spaceSlug, String apiSlug,
            Map<String, Object> payload) {
        Space space = requireSpaceAccess(authContext, spaceSlug, UserRole.VIEWER);
        DynamicSpaceApiConfig api = findBySlug(normalizeSettings(dynamicSpaceApiRepository.load(space.getId())),
                normalizeSlug(apiSlug));
        if (Boolean.FALSE.equals(api.getEnabled())) {
            throw new IllegalArgumentException("Dynamic API is disabled: " + api.getSlug());
        }
        ResolvedModel resolvedModel = resolveModel(api.getModelConfigId());
        return runAgentLoop(space.getId(), api, resolvedModel, payload != null ? payload : Map.of());
    }

    private DynamicSpaceApiRunResult runAgentLoop(String spaceId, DynamicSpaceApiConfig api,
            ResolvedModel resolvedModel, Map<String, Object> payload) {
        List<LlmChatMessage> messages = new ArrayList<>();
        messages.add(LlmChatMessage.builder()
                .role("user")
                .content(buildUserMessage(payload))
                .build());

        int toolCallCount = 0;
        int maxIterations = normalizeMaxIterations(api.getMaxIterations());
        for (int iteration = 1; iteration <= maxIterations; iteration++) {
            LlmChatResponse response = llmChatPort.chat(LlmChatRequest.builder()
                    .provider(resolvedModel.provider())
                    .model(resolvedModel.model())
                    .systemPrompt(buildSystemPrompt(api))
                    .messages(new ArrayList<>(messages))
                    .tools(dynamicSpaceApiToolPort.definitions())
                    .build());

            if (response == null) {
                throw new IllegalStateException("LLM provider returned an empty response");
            }
            if (!response.hasToolCalls()) {
                String content = response.getContent();
                if (content == null || content.isBlank()) {
                    throw new IllegalStateException("LLM provider returned no final content");
                }
                return DynamicSpaceApiRunResult.builder()
                        .apiId(api.getId())
                        .apiSlug(api.getSlug())
                        .result(parseJsonResult(content))
                        .rawResponse(content)
                        .iterations(iteration)
                        .toolCallCount(toolCallCount)
                        .build();
            }

            List<LlmToolCall> toolCalls = response.getToolCalls();
            toolCallCount += toolCalls.size();
            messages.add(LlmChatMessage.builder()
                    .role("assistant")
                    .content(response.getContent())
                    .toolCalls(toolCalls)
                    .build());
            for (LlmToolCall toolCall : toolCalls) {
                LlmToolResult toolResult = dynamicSpaceApiToolPort.execute(spaceId, toolCall);
                messages.add(LlmChatMessage.builder()
                        .role("tool")
                        .toolCallId(toolCall.getId())
                        .toolName(toolCall.getName())
                        .content(serializeToolResult(toolResult))
                        .build());
            }
        }
        throw new IllegalStateException("Dynamic API agent loop reached the iteration limit");
    }

    private ResolvedModel resolveModel(String modelConfigId) {
        String id = requireTrimmed(modelConfigId, "model config id");
        LlmSettings settings = normalizeLlmSettings(llmSettingsRepository.load());
        LlmModelConfig model = settings.getModels().stream()
                .filter(candidate -> id.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new WikiNotFoundException("LLM model config not found: " + id));
        if (model.getKind() != LlmModelKind.CHAT) {
            throw new IllegalArgumentException("Dynamic APIs require a chat model config");
        }
        if (Boolean.FALSE.equals(model.getEnabled())) {
            throw new IllegalArgumentException("LLM model config is disabled: " + id);
        }
        LlmProviderConfig provider = settings.getProviders().get(model.getProvider());
        if (provider == null) {
            throw new WikiNotFoundException("LLM provider not found: " + model.getProvider());
        }
        if (!Secret.hasValue(provider.getApiKey())) {
            throw new IllegalArgumentException("LLM provider API key is not configured: " + model.getProvider());
        }
        return new ResolvedModel(provider, model);
    }

    private DynamicSpaceApiConfig normalizeApi(SaveCommand command, DynamicSpaceApiConfig existing, String slug) {
        Instant now = Instant.now();
        return DynamicSpaceApiConfig.builder()
                .id(existing != null ? existing.getId() : UUID.randomUUID().toString())
                .slug(slug)
                .name(trimToNull(command.getName()) != null ? command.getName().trim() : slug)
                .description(trimToNull(command.getDescription()))
                .modelConfigId(requireTrimmed(command.getModelConfigId(), "model config id"))
                .systemPrompt(requireTrimmed(command.getSystemPrompt(), "system prompt"))
                .enabled(command.getEnabled() == null || command.getEnabled())
                .maxIterations(normalizeMaxIterations(command.getMaxIterations()))
                .createdAt(existing != null && existing.getCreatedAt() != null ? existing.getCreatedAt() : now)
                .updatedAt(now)
                .build();
    }

    private void ensureUniqueSlug(DynamicSpaceApiSettings settings, String currentId, String slug) {
        boolean duplicate = settings.getApis().stream()
                .anyMatch(api -> slug.equals(api.getSlug()) && (currentId == null || !currentId.equals(api.getId())));
        if (duplicate) {
            throw new IllegalArgumentException("Dynamic API already exists: " + slug);
        }
    }

    private DynamicSpaceApiConfig findById(DynamicSpaceApiSettings settings, String apiId) {
        String id = requireTrimmed(apiId, "dynamic API id");
        return settings.getApis().stream()
                .filter(api -> id.equals(api.getId()))
                .findFirst()
                .orElseThrow(() -> new WikiNotFoundException("Dynamic API not found: " + id));
    }

    private DynamicSpaceApiConfig findBySlug(DynamicSpaceApiSettings settings, String slug) {
        return settings.getApis().stream()
                .filter(api -> slug.equals(api.getSlug()))
                .findFirst()
                .orElseThrow(() -> new WikiNotFoundException("Dynamic API not found: " + slug));
    }

    private Space requireSpaceAccess(AuthContext authContext, String spaceSlug, UserRole requiredRole) {
        Space space = spaceRepository.findBySlug(spaceSlug)
                .orElseThrow(() -> new WikiNotFoundException("Space not found: " + spaceSlug));
        if (!authContext.isAuthDisabled() && !authContext.isAuthenticated() && !authContext.isReadOnlyAnonymous()) {
            throw new AuthUnauthorizedException("Authentication required");
        }
        if (!authContext.canAccessSpace(space.getId(), requiredRole)) {
            throw new AuthAccessDeniedException("Access to space '" + spaceSlug + "' denied");
        }
        return space;
    }

    private String buildSystemPrompt(DynamicSpaceApiConfig api) {
        return api.getSystemPrompt().trim() + "\n\n" + FINAL_JSON_INSTRUCTION;
    }

    private String buildUserMessage(Map<String, Object> payload) {
        return "Dynamic API request payload:\n" + writeJson(payload);
    }

    private String serializeToolResult(LlmToolResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", result != null && result.isSuccess());
        if (result != null) {
            body.put("output", result.getOutput());
            body.put("data", result.getData());
            body.put("error", result.getError());
        } else {
            body.put("error", "Tool returned no result");
        }
        return writeJson(body);
    }

    private Object parseJsonResult(String content) {
        String normalized = stripJsonFence(content.trim());
        try {
            return jsonCodecPort.read(normalized);
        } catch (RuntimeException exception) {
            return Map.of("text", content);
        }
    }

    private String stripJsonFence(String content) {
        if (!content.startsWith("```")) {
            return content;
        }
        int firstLineEnd = content.indexOf('\n');
        int lastFence = content.lastIndexOf("```");
        if (firstLineEnd > 0 && lastFence > firstLineEnd) {
            return content.substring(firstLineEnd + 1, lastFence).trim();
        }
        return content;
    }

    private String writeJson(Object value) {
        try {
            return jsonCodecPort.write(value);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to serialize dynamic API payload", exception);
        }
    }

    private DynamicSpaceApiSettings normalizeSettings(DynamicSpaceApiSettings settings) {
        if (settings == null) {
            return DynamicSpaceApiSettings.builder().build();
        }
        if (settings.getApis() == null) {
            settings.setApis(new ArrayList<>());
        }
        return settings;
    }

    private LlmSettings normalizeLlmSettings(LlmSettings settings) {
        if (settings == null) {
            return LlmSettings.builder().build();
        }
        if (settings.getProviders() == null) {
            settings.setProviders(new LinkedHashMap<>());
        }
        if (settings.getModels() == null) {
            settings.setModels(new ArrayList<>());
        }
        return settings;
    }

    private String normalizeSlug(String value) {
        String slug = requireTrimmed(value, "dynamic API slug").toLowerCase(Locale.ROOT);
        if (!API_SLUG_PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException("Dynamic API slug must match [a-z0-9][a-z0-9_-]*");
        }
        return slug;
    }

    private int normalizeMaxIterations(Integer value) {
        if (value == null) {
            return DEFAULT_MAX_ITERATIONS;
        }
        if (value < 1 || value > MAX_ITERATIONS) {
            throw new IllegalArgumentException("max iterations must be between 1 and " + MAX_ITERATIONS);
        }
        return value;
    }

    private String requireTrimmed(String value, String fieldName) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record ResolvedModel(LlmProviderConfig provider, LlmModelConfig model) {
    }

    @Value
    @Builder
    public static class SaveCommand {
        String slug;
        String name;
        String description;
        String modelConfigId;
        String systemPrompt;
        Boolean enabled;
        Integer maxIterations;
    }
}
