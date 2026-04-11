package dev.golemcore.brain.adapter.in.web;

import dev.golemcore.brain.adapter.in.web.auth.AuthContextResolver;
import dev.golemcore.brain.application.service.llm.LlmSettingsService;
import dev.golemcore.brain.domain.Secret;
import dev.golemcore.brain.domain.auth.AuthContext;
import dev.golemcore.brain.domain.llm.LlmApiType;
import dev.golemcore.brain.domain.llm.LlmModelConfig;
import dev.golemcore.brain.domain.llm.LlmModelKind;
import dev.golemcore.brain.domain.llm.LlmProviderCheckResult;
import dev.golemcore.brain.domain.llm.LlmProviderConfig;
import dev.golemcore.brain.domain.llm.LlmSettings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmSettingsController {

    private final LlmSettingsService llmSettingsService;
    private final AuthContextResolver authContextResolver;

    @GetMapping("/settings")
    public LlmSettings getSettings(HttpServletRequest request) {
        return llmSettingsService.getSettings(resolveContext(request));
    }

    @PostMapping("/providers")
    public ResponseEntity<LlmSettings> createProvider(
            @Valid @RequestBody SaveProviderRequest payload,
            HttpServletRequest request) {
        LlmSettings settings = llmSettingsService.createProvider(
                resolveContext(request),
                payload.getName(),
                toProviderConfig(payload));
        return ResponseEntity.status(201).body(settings);
    }

    @PutMapping("/providers/{name}")
    public LlmSettings updateProvider(
            @PathVariable String name,
            @Valid @RequestBody SaveProviderRequest payload,
            HttpServletRequest request) {
        return llmSettingsService.updateProvider(resolveContext(request), name, toProviderConfig(payload));
    }

    @DeleteMapping("/providers/{name}")
    public LlmSettings deleteProvider(@PathVariable String name, HttpServletRequest request) {
        return llmSettingsService.deleteProvider(resolveContext(request), name);
    }

    @PostMapping("/providers/{name}/check")
    public LlmProviderCheckResult checkProvider(@PathVariable String name, HttpServletRequest request) {
        return llmSettingsService.checkProvider(resolveContext(request), name);
    }

    @PostMapping("/models")
    public ResponseEntity<LlmSettings> createModel(
            @Valid @RequestBody SaveModelRequest payload,
            HttpServletRequest request) {
        LlmSettings settings = llmSettingsService.createModel(resolveContext(request), toModelConfig(payload));
        return ResponseEntity.status(201).body(settings);
    }

    @PutMapping("/models/{id}")
    public LlmSettings updateModel(
            @PathVariable String id,
            @Valid @RequestBody SaveModelRequest payload,
            HttpServletRequest request) {
        return llmSettingsService.updateModel(resolveContext(request), id, toModelConfig(payload));
    }

    @DeleteMapping("/models/{id}")
    public LlmSettings deleteModel(@PathVariable String id, HttpServletRequest request) {
        return llmSettingsService.deleteModel(resolveContext(request), id);
    }

    private AuthContext resolveContext(HttpServletRequest request) {
        return authContextResolver.requireAuthenticated(request);
    }

    private LlmProviderConfig toProviderConfig(SaveProviderRequest payload) {
        return LlmProviderConfig.builder()
                .apiKey(payload.getApiKey())
                .baseUrl(payload.getBaseUrl())
                .requestTimeoutSeconds(payload.getRequestTimeoutSeconds())
                .apiType(payload.getApiType())
                .build();
    }

    private LlmModelConfig toModelConfig(SaveModelRequest payload) {
        return LlmModelConfig.builder()
                .provider(payload.getProvider())
                .modelId(payload.getModelId())
                .displayName(payload.getDisplayName())
                .kind(payload.getKind())
                .enabled(payload.getEnabled())
                .maxInputTokens(payload.getMaxInputTokens())
                .dimensions(payload.getDimensions())
                .temperature(payload.getTemperature())
                .build();
    }

    @Data
    public static class SaveProviderRequest {
        @Size(max = 80)
        private String name;

        private Secret apiKey;

        @Size(max = 500)
        private String baseUrl;

        private Integer requestTimeoutSeconds;
        private LlmApiType apiType;
    }

    @Data
    public static class SaveModelRequest {
        @NotBlank
        @Size(max = 80)
        private String provider;

        @NotBlank
        @Size(max = 240)
        private String modelId;

        @Size(max = 240)
        private String displayName;

        private LlmModelKind kind;
        private Boolean enabled;
        private Integer maxInputTokens;
        private Integer dimensions;
        private Double temperature;
    }
}
