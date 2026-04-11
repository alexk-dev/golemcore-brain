package dev.golemcore.brain.application.service.llm;

import dev.golemcore.brain.application.exception.WikiNotFoundException;
import dev.golemcore.brain.application.port.out.LlmProviderCheckPort;
import dev.golemcore.brain.application.port.out.LlmSettingsRepository;
import dev.golemcore.brain.application.service.auth.AuthAccessDeniedException;
import dev.golemcore.brain.domain.Secret;
import dev.golemcore.brain.domain.auth.AuthContext;
import dev.golemcore.brain.domain.auth.UserRole;
import dev.golemcore.brain.domain.llm.LlmApiType;
import dev.golemcore.brain.domain.llm.LlmModelConfig;
import dev.golemcore.brain.domain.llm.LlmModelKind;
import dev.golemcore.brain.domain.llm.LlmProviderCheckResult;
import dev.golemcore.brain.domain.llm.LlmProviderConfig;
import dev.golemcore.brain.domain.llm.LlmSettings;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    public LlmSettings createModel(AuthContext authContext, LlmModelConfig modelConfig) {
        requireAdminAccount(authContext);
        LlmSettings settings = normalizeSettings(llmSettingsRepository.load());
        LlmModelConfig normalized = normalizeModel(settings, modelConfig, null);
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
        LlmModelConfig normalized = normalizeModel(settings, modelConfig, existing);
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
        return LlmProviderConfig.builder()
                .apiKey(apiKey)
                .baseUrl(trimToNull(providerConfig.getBaseUrl()))
                .requestTimeoutSeconds(normalizeTimeout(providerConfig.getRequestTimeoutSeconds()))
                .apiType(providerConfig.getApiType() != null
                        ? providerConfig.getApiType()
                        : defaultApiType(providerName))
                .createdAt(existing != null && existing.getCreatedAt() != null ? existing.getCreatedAt() : now)
                .updatedAt(now)
                .build();
    }

    private LlmModelConfig normalizeModel(LlmSettings settings, LlmModelConfig modelConfig, LlmModelConfig existing) {
        if (modelConfig == null) {
            throw new IllegalArgumentException("model config is required");
        }
        String providerName = normalizeProviderName(modelConfig.getProvider());
        if (!settings.getProviders().containsKey(providerName)) {
            throw new WikiNotFoundException("LLM provider not found: " + providerName);
        }
        String modelName = requireTrimmed(modelConfig.getModelId(), "model id");
        LlmModelKind kind = modelConfig.getKind() != null ? modelConfig.getKind() : LlmModelKind.CHAT;
        String id = existing != null ? existing.getId() : UUID.randomUUID().toString();
        ensureUniqueModel(settings, id, providerName, kind, modelName);

        Instant now = Instant.now();
        return LlmModelConfig.builder()
                .id(id)
                .provider(providerName)
                .modelId(modelName)
                .displayName(trimToNull(modelConfig.getDisplayName()))
                .kind(kind)
                .enabled(modelConfig.getEnabled() == null || modelConfig.getEnabled())
                .maxInputTokens(normalizePositiveInteger(modelConfig.getMaxInputTokens(), "max input tokens"))
                .dimensions(kind == LlmModelKind.EMBEDDING
                        ? normalizePositiveInteger(modelConfig.getDimensions(), "embedding dimensions")
                        : null)
                .temperature(kind == LlmModelKind.CHAT
                        ? normalizeTemperature(modelConfig.getTemperature())
                        : null)
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
                .createdAt(provider.getCreatedAt())
                .updatedAt(provider.getUpdatedAt())
                .build()));
        return LlmSettings.builder()
                .providers(providers)
                .models(new ArrayList<>(normalized.getModels()))
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

    private LlmApiType defaultApiType(String providerName) {
        return switch (providerName) {
        case "anthropic" -> LlmApiType.ANTHROPIC;
        case "gemini", "google" -> LlmApiType.GEMINI;
        default -> LlmApiType.OPENAI;
        };
    }
}
