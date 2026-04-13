package dev.golemcore.brain.application.service.llm;

import dev.golemcore.brain.application.exception.WikiNotFoundException;
import dev.golemcore.brain.application.port.out.LlmChatPort;
import dev.golemcore.brain.application.port.out.LlmEmbeddingPort;
import dev.golemcore.brain.application.port.out.LlmProviderCheckPort;
import dev.golemcore.brain.application.port.out.LlmSettingsRepository;
import dev.golemcore.brain.application.service.auth.AuthAccessDeniedException;
import dev.golemcore.brain.domain.Secret;
import dev.golemcore.brain.domain.auth.AuthContext;
import dev.golemcore.brain.domain.auth.UserRole;
import dev.golemcore.brain.domain.llm.LlmApiType;
import dev.golemcore.brain.domain.llm.LlmChatMessage;
import dev.golemcore.brain.domain.llm.LlmChatRequest;
import dev.golemcore.brain.domain.llm.LlmEmbeddingRequest;
import dev.golemcore.brain.domain.llm.LlmModelConfig;
import dev.golemcore.brain.domain.llm.LlmModelKind;
import dev.golemcore.brain.domain.llm.LlmProviderCheckResult;
import dev.golemcore.brain.domain.llm.LlmProviderConfig;
import dev.golemcore.brain.domain.llm.LlmSettings;
import dev.golemcore.brain.domain.llm.ModelCatalogEntry;
import dev.golemcore.brain.domain.llm.ModelReasoningLevel;
import dev.golemcore.brain.domain.llm.ModelRegistryConfig;
import dev.golemcore.brain.domain.llm.ModelRegistryResolveResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LlmSettingsService {

    private static final Pattern PROVIDER_NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]*$");
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    private final LlmSettingsRepository llmSettingsRepository;
    private final LlmProviderCheckPort llmProviderCheckPort;
    private final LlmChatPort llmChatPort;
    private final LlmEmbeddingPort llmEmbeddingPort;
    private final ModelRegistryService modelRegistryService;

    public LlmSettings getSettings(AuthContext authContext) {
        requireAdminAccount(authContext);
        return redact(llmSettingsRepository.load());
    }

    public LlmSettings createProvider(AuthContext authContext, String name, LlmProviderConfig providerConfig) {
        requireAdminAccount(authContext);
        String providerName = normalizeProviderName(name);
        LlmSettings settings = normalizeSettings(llmSettingsRepository.load());
        if (settings.getProviders().containsKey(providerName)) {
            throw new IllegalArgumentException("LLM provider already exists: " + providerName);
        }
        settings.getProviders().put(providerName, normalizeProvider(providerName, providerConfig, null));
        return redact(llmSettingsRepository.save(settings));
    }

    public LlmSettings updateProvider(AuthContext authContext, String name, LlmProviderConfig providerConfig) {
        requireAdminAccount(authContext);
        String providerName = normalizeProviderName(name);
        LlmSettings settings = normalizeSettings(llmSettingsRepository.load());
        LlmProviderConfig existing = settings.getProviders().get(providerName);
        if (existing == null) {
            throw new WikiNotFoundException("LLM provider not found: " + providerName);
        }
        settings.getProviders().put(providerName, normalizeProvider(providerName, providerConfig, existing));
        return redact(llmSettingsRepository.save(settings));
    }

    public LlmSettings deleteProvider(AuthContext authContext, String name) {
        requireAdminAccount(authContext);
        String providerName = normalizeProviderName(name);
        LlmSettings settings = normalizeSettings(llmSettingsRepository.load());
        if (!settings.getProviders().containsKey(providerName)) {
            throw new WikiNotFoundException("LLM provider not found: " + providerName);
        }
        boolean hasModels = settings.getModels().stream()
                .anyMatch(model -> providerName.equals(model.getProvider()));
        if (hasModels) {
            throw new IllegalArgumentException("Remove models for provider '" + providerName + "' first");
        }
        settings.getProviders().remove(providerName);
        return redact(llmSettingsRepository.save(settings));
    }

    public LlmProviderCheckResult checkProvider(AuthContext authContext, String name) {
        requireAdminAccount(authContext);
        String providerName = normalizeProviderName(name);
        LlmProviderConfig providerConfig = normalizeSettings(llmSettingsRepository.load()).getProviders()
                .get(providerName);
        if (providerConfig == null) {
            throw new WikiNotFoundException("LLM provider not found: " + providerName);
        }
        return llmProviderCheckPort.check(providerName, providerConfig);
    }

    public LlmProviderCheckResult checkProviderConfig(
            AuthContext authContext,
            String name,
            LlmProviderConfig providerConfig) {
        requireAdminAccount(authContext);
        String providerName = normalizeProviderName(name);
        LlmProviderConfig existing = normalizeSettings(llmSettingsRepository.load()).getProviders().get(providerName);
        return llmProviderCheckPort.check(providerName, normalizeProvider(providerName, providerConfig, existing));
    }

    public LlmProviderCheckResult checkModel(AuthContext authContext, LlmModelConfig modelConfig) {
        requireAdminAccount(authContext);
        LlmSettings settings = normalizeSettings(llmSettingsRepository.load());
        LlmModelConfig normalized = normalizeModel(settings, modelConfig, null, false);
        LlmProviderConfig providerConfig = settings.getProviders().get(normalized.getProvider());
        try {
            if (normalized.getKind() == LlmModelKind.EMBEDDING) {
                llmEmbeddingPort.embed(LlmEmbeddingRequest.builder()
                        .provider(providerConfig)
                        .model(normalized)
                        .inputs(List.of("health-check"))
                        .build());
            } else {
                llmChatPort.chat(LlmChatRequest.builder()
                        .provider(providerConfig)
                        .model(normalized)
                        .messages(List.of(LlmChatMessage.builder().role("user").content("Say OK.").build()))
                        .build());
            }
            return new LlmProviderCheckResult(true, "Model test completed", null);
        } catch (RuntimeException exception) {
            return new LlmProviderCheckResult(false, "Model test failed: " + exception.getMessage(), null);
        }
    }

    public LlmSettings createModel(AuthContext authContext, LlmModelConfig modelConfig) {
        requireAdminAccount(authContext);
        LlmSettings settings = normalizeSettings(llmSettingsRepository.load());
        LlmModelConfig normalized = normalizeModel(settings, modelConfig, null, true);
        settings.getModels().add(normalized);
        return redact(llmSettingsRepository.save(settings));
    }

    public LlmSettings updateModel(AuthContext authContext, String id, LlmModelConfig modelConfig) {
        requireAdminAccount(authContext);
        String modelId = requireTrimmed(id, "model id");
        LlmSettings settings = normalizeSettings(llmSettingsRepository.load());
        LlmModelConfig existing = settings.getModels().stream()
                .filter(model -> modelId.equals(model.getId()))
                .findFirst()
                .orElseThrow(() -> new WikiNotFoundException("LLM model config not found: " + modelId));
        LlmModelConfig normalized = normalizeModel(settings, modelConfig, existing, true);
        settings.getModels().removeIf(model -> modelId.equals(model.getId()));
        settings.getModels().add(normalized);
        return redact(llmSettingsRepository.save(settings));
    }

    public LlmSettings deleteModel(AuthContext authContext, String id) {
        requireAdminAccount(authContext);
        String modelId = requireTrimmed(id, "model id");
        LlmSettings settings = normalizeSettings(llmSettingsRepository.load());
        boolean removed = settings.getModels().removeIf(model -> modelId.equals(model.getId()));
        if (!removed) {
            throw new WikiNotFoundException("LLM model config not found: " + modelId);
        }
        return redact(llmSettingsRepository.save(settings));
    }

    public ModelRegistryConfig getModelRegistryConfig(AuthContext authContext) {
        requireAdminAccount(authContext);
        return modelRegistryService.getConfig();
    }

    public LlmSettings updateModelRegistryConfig(AuthContext authContext, ModelRegistryConfig config) {
        requireAdminAccount(authContext);
        return redact(modelRegistryService.updateConfig(config));
    }

    public ModelRegistryResolveResult resolveModelRegistry(
            AuthContext authContext,
            String provider,
            String modelId) {
        requireAdminAccount(authContext);
        return modelRegistryService.resolveDefaults(provider, modelId);
    }

    private void requireAdminAccount(AuthContext authContext) {
        if (authContext.isApiKey()) {
            throw new AuthAccessDeniedException("Admin account session required");
        }
        if (!authContext.isAuthenticated()
                || authContext.getUser() == null
                || authContext.getUser().getRole() != UserRole.ADMIN
                || !authContext.isGlobalAdmin()) {
            throw new AuthAccessDeniedException("Admin account session required");
        }
    }

    private LlmProviderConfig normalizeProvider(
            String providerName,
            LlmProviderConfig providerConfig,
            LlmProviderConfig existing) {
        if (providerConfig == null) {
            throw new IllegalArgumentException("provider config is required");
        }
        Instant now = Instant.now();
        Secret apiKey = normalizeSecret(providerConfig.getApiKey());
        if (existing != null && !Secret.hasValue(apiKey)) {
            apiKey = existing.getApiKey();
        }
        LlmApiType apiType = providerConfig.getApiType() != null
                ? providerConfig.getApiType()
                : defaultApiType(providerName);
        return LlmProviderConfig.builder()
                .apiKey(apiKey)
                .baseUrl(trimToNull(providerConfig.getBaseUrl()))
                .requestTimeoutSeconds(normalizeTimeout(providerConfig.getRequestTimeoutSeconds()))
                .apiType(apiType)
                .legacyApi(resolveLegacyApi(apiType, providerConfig, existing))
                .createdAt(existing != null && existing.getCreatedAt() != null ? existing.getCreatedAt() : now)
                .updatedAt(now)
                .build();
    }

    private Boolean resolveLegacyApi(
            LlmApiType apiType,
            LlmProviderConfig providerConfig,
            LlmProviderConfig existing) {
        if (apiType != LlmApiType.OPENAI) {
            return null;
        }
        if (providerConfig.getLegacyApi() != null) {
            return providerConfig.getLegacyApi();
        }
        if (existing != null && existing.getLegacyApi() != null) {
            return existing.getLegacyApi();
        }
        return false;
    }

    private LlmModelConfig normalizeModel(
            LlmSettings settings,
            LlmModelConfig modelConfig,
            LlmModelConfig existing,
            boolean enforceUnique) {
        if (modelConfig == null) {
            throw new IllegalArgumentException("model config is required");
        }
        String providerName = normalizeProviderName(modelConfig.getProvider());
        if (!settings.getProviders().containsKey(providerName)) {
            throw new WikiNotFoundException("LLM provider not found: " + providerName);
        }
        String modelName = requireTrimmed(modelConfig.getModelId(), "model id");
        LlmModelKind kind = modelConfig.getKind() != null ? modelConfig.getKind() : LlmModelKind.CHAT;
        ModelCatalogEntry catalogEntry = kind == LlmModelKind.CHAT
                ? modelRegistryService.resolveDefaults(providerName, modelName).defaultSettings()
                : null;
        String id = existing != null ? existing.getId() : UUID.randomUUID().toString();
        if (enforceUnique) {
            ensureUniqueModel(settings, id, providerName, kind, modelName);
        }

        Instant now = Instant.now();
        boolean supportsTemperature = resolveSupportsTemperature(kind, modelConfig, catalogEntry);
        String reasoningEffort = kind == LlmModelKind.CHAT
                ? resolveReasoningEffort(modelConfig, catalogEntry)
                : null;
        return LlmModelConfig.builder()
                .id(id)
                .provider(providerName)
                .modelId(modelName)
                .displayName(trimToNull(modelConfig.getDisplayName()))
                .kind(kind)
                .enabled(modelConfig.getEnabled() == null || modelConfig.getEnabled())
                .supportsTemperature(supportsTemperature)
                .maxInputTokens(resolveMaxInputTokens(kind, modelConfig, catalogEntry))
                .dimensions(kind == LlmModelKind.EMBEDDING
                        ? normalizePositiveInteger(modelConfig.getDimensions(), "embedding dimensions")
                        : null)
                .temperature(kind == LlmModelKind.CHAT
                        && reasoningEffort == null
                        && supportsTemperature
                                ? normalizeTemperature(modelConfig.getTemperature())
                                : null)
                .reasoningEffort(reasoningEffort)
                .createdAt(existing != null && existing.getCreatedAt() != null ? existing.getCreatedAt() : now)
                .updatedAt(now)
                .build();
    }

    private void ensureUniqueModel(
            LlmSettings settings,
            String id,
            String providerName,
            LlmModelKind kind,
            String modelName) {
        boolean duplicate = settings.getModels().stream()
                .anyMatch(model -> !id.equals(model.getId())
                        && providerName.equals(model.getProvider())
                        && kind == model.getKind()
                        && modelName.equals(model.getModelId()));
        if (duplicate) {
            throw new IllegalArgumentException("Model config already exists for provider '" + providerName + "'");
        }
    }

    private LlmSettings redact(LlmSettings settings) {
        LlmSettings normalized = normalizeSettings(settings);
        Map<String, LlmProviderConfig> providers = new LinkedHashMap<>();
        normalized.getProviders().forEach((name, provider) -> providers.put(name, LlmProviderConfig.builder()
                .apiKey(Secret.redacted(provider.getApiKey()))
                .baseUrl(provider.getBaseUrl())
                .requestTimeoutSeconds(provider.getRequestTimeoutSeconds())
                .apiType(provider.getApiType())
                .legacyApi(provider.getLegacyApi())
                .createdAt(provider.getCreatedAt())
                .updatedAt(provider.getUpdatedAt())
                .build()));
        return LlmSettings.builder()
                .providers(providers)
                .models(new ArrayList<>(normalized.getModels()))
                .modelRegistry(normalized.getModelRegistry())
                .build();
    }

    private LlmSettings normalizeSettings(LlmSettings settings) {
        if (settings == null) {
            return LlmSettings.builder().build();
        }
        if (settings.getProviders() == null) {
            settings.setProviders(new LinkedHashMap<>());
        }
        if (settings.getModels() == null) {
            settings.setModels(new ArrayList<>());
        }
        if (settings.getModelRegistry() == null) {
            settings.setModelRegistry(ModelRegistryConfig.builder().build());
        }
        return settings;
    }

    private Secret normalizeSecret(Secret secret) {
        if (secret == null) {
            return null;
        }
        if (secret.getEncrypted() == null) {
            secret.setEncrypted(false);
        }
        if (secret.getPresent() == null) {
            secret.setPresent(Secret.hasValue(secret));
        } else if (Secret.hasValue(secret)) {
            secret.setPresent(true);
        }
        return secret;
    }

    private String normalizeProviderName(String rawName) {
        String name = requireTrimmed(rawName, "provider name").toLowerCase(Locale.ROOT);
        if (!PROVIDER_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Provider name must match [a-z0-9][a-z0-9_-]*");
        }
        return name;
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

    private Integer normalizeTimeout(Integer timeoutSeconds) {
        if (timeoutSeconds == null) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        if (timeoutSeconds < 1 || timeoutSeconds > 3600) {
            throw new IllegalArgumentException("request timeout must be between 1 and 3600 seconds");
        }
        return timeoutSeconds;
    }

    private Integer normalizePositiveInteger(Integer value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value < 1) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
        return value;
    }

    private Double normalizeTemperature(Double value) {
        if (value == null) {
            return null;
        }
        if (value < 0.0 || value > 2.0) {
            throw new IllegalArgumentException("temperature must be between 0 and 2");
        }
        return value;
    }

    private boolean resolveSupportsTemperature(
            LlmModelKind kind,
            LlmModelConfig modelConfig,
            ModelCatalogEntry catalogEntry) {
        if (kind != LlmModelKind.CHAT) {
            return false;
        }
        if (modelConfig.getSupportsTemperature() != null) {
            return modelConfig.getSupportsTemperature();
        }
        return catalogEntry == null || catalogEntry.getSupportsTemperature() == null
                || catalogEntry.getSupportsTemperature();
    }

    private Integer resolveMaxInputTokens(
            LlmModelKind kind,
            LlmModelConfig modelConfig,
            ModelCatalogEntry catalogEntry) {
        Integer explicit = normalizePositiveInteger(modelConfig.getMaxInputTokens(), "max input tokens");
        if (explicit != null || kind != LlmModelKind.CHAT || catalogEntry == null) {
            return explicit;
        }
        Integer catalogMax = normalizePositiveInteger(catalogEntry.getMaxInputTokens(), "catalog max input tokens");
        if (catalogEntry.getReasoning() == null || catalogEntry.getReasoning().getDefaultLevel() == null) {
            return catalogMax;
        }
        ModelReasoningLevel level = catalogEntry.getReasoning().getLevels().get(
                catalogEntry.getReasoning().getDefaultLevel());
        if (level == null || level.getMaxInputTokens() == null) {
            return catalogMax;
        }
        return normalizePositiveInteger(level.getMaxInputTokens(), "catalog reasoning max input tokens");
    }

    private String resolveReasoningEffort(LlmModelConfig modelConfig, ModelCatalogEntry catalogEntry) {
        String explicit = trimToNull(modelConfig.getReasoningEffort());
        if (explicit != null) {
            return "none".equalsIgnoreCase(explicit) ? null : explicit;
        }
        if (catalogEntry == null || catalogEntry.getReasoning() == null) {
            return null;
        }
        return normalizeReasoningEffort(catalogEntry.getReasoning().getDefaultLevel());
    }

    private String normalizeReasoningEffort(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null || "none".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private LlmApiType defaultApiType(String providerName) {
        return switch (providerName) {
        case "anthropic" -> LlmApiType.ANTHROPIC;
        case "gemini", "google" -> LlmApiType.GEMINI;
        default -> LlmApiType.OPENAI;
        };
    }
}
