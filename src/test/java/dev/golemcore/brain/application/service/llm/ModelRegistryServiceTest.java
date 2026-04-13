package dev.golemcore.brain.application.service.llm;

import dev.golemcore.brain.application.port.out.LlmSettingsRepository;
import dev.golemcore.brain.application.port.out.ModelRegistryCachePort;
import dev.golemcore.brain.application.port.out.ModelRegistryDocumentPort;
import dev.golemcore.brain.application.port.out.ModelRegistryRemotePort;
import dev.golemcore.brain.domain.llm.LlmSettings;
import dev.golemcore.brain.domain.llm.ModelCatalogEntry;
import dev.golemcore.brain.domain.llm.ModelRegistryResolveResult;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelRegistryServiceTest {

    @Test
    void shouldResolveSharedModelDefaultsFromGolemcoreModels() {
        RecordingRemotePort remotePort = new RecordingRemotePort(Map.of(
                "models/gpt-5.4.json", """
                        {
                          "displayName": "GPT-5.4",
                          "supportsTemperature": false,
                          "maxInputTokens": 1050000,
                          "reasoning": {
                            "default": "high",
                            "levels": {
                              "high": { "maxInputTokens": 1050000 }
                            }
                          }
                        }
                        """));
        ModelRegistryService service = new ModelRegistryService(
                new InMemoryLlmSettingsRepository(),
                remotePort,
                new InMemoryCachePort(),
                new JacksonDocumentPort());

        ModelRegistryResolveResult result = service.resolveDefaults("openai", "gpt-5.4-2026-04-01");

        assertEquals("shared", result.configSource());
        assertEquals("remote-hit", result.cacheStatus());
        assertNotNull(result.defaultSettings());
        assertEquals("openai", result.defaultSettings().getProvider());
        assertEquals("GPT-5.4", result.defaultSettings().getDisplayName());
        assertEquals(false, result.defaultSettings().getSupportsTemperature());
        assertEquals(1050000, result.defaultSettings().getMaxInputTokens());
        assertEquals("high", result.defaultSettings().getReasoning().getDefaultLevel());
        assertTrue(remotePort.requestedUris.stream().anyMatch(uri -> uri.toString()
                .contains("/alexk-dev/golemcore-models/main/models/gpt-5.4.json")));
    }

    @Test
    void shouldCacheMissingProviderSpecificLookupAndUseSharedDefaults() {
        InMemoryCachePort cachePort = new InMemoryCachePort();
        RecordingRemotePort remotePort = new RecordingRemotePort(Map.of(
                "models/gpt-5.1.json", """
                        {
                          "displayName": "GPT-5.1",
                          "supportsTemperature": false,
                          "maxInputTokens": 400000,
                          "reasoning": {
                            "default": "none",
                            "levels": {
                              "none": { "maxInputTokens": 400000 }
                            }
                          }
                        }
                        """));
        ModelRegistryService service = new ModelRegistryService(
                new InMemoryLlmSettingsRepository(),
                remotePort,
                cachePort,
                new JacksonDocumentPort());

        ModelRegistryResolveResult result = service.resolveDefaults("openai", "gpt-5.1");

        assertEquals("shared", result.configSource());
        assertEquals("remote-hit", result.cacheStatus());
        assertFalse(cachePort.entries.get(cachePort.cacheKey(
                "https://github.com/alexk-dev/golemcore-models",
                "main",
                "providers/openai/gpt-5.1.json")).found());
    }

    private static class InMemoryLlmSettingsRepository implements LlmSettingsRepository {
        private LlmSettings settings = LlmSettings.builder().build();

        @Override
        public void initialize() {
        }

        @Override
        public LlmSettings load() {
            return settings;
        }

        @Override
        public LlmSettings save(LlmSettings settings) {
            this.settings = settings;
            return settings;
        }
    }

    private static class RecordingRemotePort implements ModelRegistryRemotePort {
        private final Map<String, String> contentByPath;
        private final java.util.List<URI> requestedUris = new java.util.ArrayList<>();

        RecordingRemotePort(Map<String, String> contentByPath) {
            this.contentByPath = contentByPath;
        }

        @Override
        public String fetchText(URI uri) {
            requestedUris.add(uri);
            String text = uri.toString();
            return contentByPath.entrySet().stream()
                    .filter(entry -> text.endsWith(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }
    }

    private static class InMemoryCachePort implements ModelRegistryCachePort {
        private final Map<String, CachedRegistryEntry> entries = new LinkedHashMap<>();

        @Override
        public CachedRegistryEntry read(String repositoryUrl, String branch, String relativePath) {
            return entries.get(cacheKey(repositoryUrl, branch, relativePath));
        }

        @Override
        public void write(String repositoryUrl, String branch, String relativePath, CachedRegistryEntry entry) {
            entries.put(cacheKey(repositoryUrl, branch, relativePath), entry);
        }

        String cacheKey(String repositoryUrl, String branch, String relativePath) {
            return repositoryUrl + "\n" + branch + "\n" + relativePath;
        }
    }

    private static class JacksonDocumentPort implements ModelRegistryDocumentPort {
        private final ObjectMapper objectMapper;

        JacksonDocumentPort() {
            objectMapper = new ObjectMapper().rebuild()
                    .addModule(new dev.golemcore.brain.adapter.out.json.BrainJsonModule())
                    .build();
        }

        @Override
        public ModelCatalogEntry parseCatalogEntry(String json) {
            return objectMapper.readValue(json, ModelCatalogEntry.class);
        }
    }
}
