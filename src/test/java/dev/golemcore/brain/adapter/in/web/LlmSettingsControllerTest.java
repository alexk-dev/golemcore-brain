package dev.golemcore.brain.adapter.in.web;

import dev.golemcore.brain.application.port.out.LlmChatPort;
import dev.golemcore.brain.application.port.out.LlmEmbeddingPort;
import dev.golemcore.brain.application.port.out.LlmProviderCheckPort;
import dev.golemcore.brain.application.port.out.LlmSettingsRepository;
import dev.golemcore.brain.application.service.auth.AuthService;
import dev.golemcore.brain.domain.llm.LlmChatResponse;
import dev.golemcore.brain.domain.llm.LlmEmbeddingResponse;
import dev.golemcore.brain.domain.llm.LlmProviderCheckResult;
import jakarta.servlet.http.Cookie;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class LlmSettingsControllerTest {

    private static Path storageRoot;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        storageRoot = tempDir.resolve("llm-settings-controller-test");
        registry.add("brain.storage-root", () -> storageRoot.toString());
        registry.add("brain.seed-demo-content", () -> "false");
        registry.add("brain.auth-disabled", () -> "false");
        registry.add("brain.public-access", () -> "false");
        registry.add("brain.admin-username", () -> "admin");
        registry.add("brain.admin-email", () -> "admin@example.com");
        registry.add("brain.admin-password", () -> "admin");
    }

    private final MockMvc mockMvc;

    private final LlmSettingsRepository llmSettingsRepository;

    LlmSettingsControllerTest(MockMvc mockMvc, LlmSettingsRepository llmSettingsRepository) {
        this.mockMvc = mockMvc;
        this.llmSettingsRepository = llmSettingsRepository;
    }

    @Test
    void shouldRedactSecretsAndPreserveExistingSecretOnUpdate() throws Exception {
        Cookie adminSession = login("admin", "admin");

        MvcResult createResult = mockMvc.perform(post("/api/llm/providers")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "openai",
                          "apiType": "openai",
                          "baseUrl": "https://api.openai.com/v1",
                          "requestTimeoutSeconds": 45,
                          "apiKey": {
                            "value": "sk-secret",
                            "encrypted": false
                          }
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.providers.openai.apiKey.present", is(true)))
                .andExpect(jsonPath("$.providers.openai.apiKey.value", nullValue()))
                .andReturn();
        assertFalse(createResult.getResponse().getContentAsString().contains("sk-secret"));

        MvcResult updateResult = mockMvc.perform(put("/api/llm/providers/openai")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "apiType": "openai",
                          "baseUrl": "https://gateway.example/v1",
                          "requestTimeoutSeconds": 60
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providers.openai.apiKey.present", is(true)))
                .andExpect(jsonPath("$.providers.openai.apiKey.value", nullValue()))
                .andExpect(jsonPath("$.providers.openai.baseUrl", is("https://gateway.example/v1")))
                .andReturn();
        assertFalse(updateResult.getResponse().getContentAsString().contains("sk-secret"));
        assertEquals("sk-secret", llmSettingsRepository.load().getProviders().get("openai").getApiKey().getValue());

        mockMvc.perform(post("/api/llm/models")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "provider": "openai",
                          "modelId": "gpt-5.4",
                          "displayName": "Reasoning Chat",
                          "kind": "chat",
                          "enabled": true,
                          "reasoningEffort": "high"
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.models[0].provider", is("openai")))
                .andExpect(jsonPath("$.models[0].modelId", is("gpt-5.4")))
                .andExpect(jsonPath("$.models[0].kind", is("chat")))
                .andExpect(jsonPath("$.models[0].temperature", nullValue()))
                .andExpect(jsonPath("$.models[0].reasoningEffort", is("high")));

        mockMvc.perform(post("/api/llm/providers/check")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "openai",
                          "apiType": "openai",
                          "baseUrl": "https://api.openai.com/v1",
                          "requestTimeoutSeconds": 45,
                          "apiKey": {
                            "value": "sk-secret",
                            "encrypted": false
                          }
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").exists());

        mockMvc.perform(post("/api/llm/models/check")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "provider": "openai",
                          "modelId": "gpt-5.4",
                          "kind": "chat",
                          "enabled": true,
                          "reasoningEffort": "medium"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").exists());
    }

    @TestConfiguration
    static class LlmTestPortConfiguration {
        @Bean
        @Primary
        LlmProviderCheckPort testLlmProviderCheckPort() {
            return (providerName, providerConfig) -> new LlmProviderCheckResult(true, "Provider test completed", 200);
        }

        @Bean
        @Primary
        LlmChatPort testLlmChatPort() {
            return request -> LlmChatResponse.builder()
                    .content("OK")
                    .finishReason("stop")
                    .build();
        }

        @Bean
        @Primary
        LlmEmbeddingPort testLlmEmbeddingPort() {
            return request -> LlmEmbeddingResponse.builder()
                    .embeddings(List.of(List.of(1.0d)))
                    .build();
        }
    }

    @Test
    void shouldRequireAdminAccountSession() throws Exception {
        Cookie adminSession = login("admin", "admin");

        mockMvc.perform(post("/api/auth/users")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "editor",
                          "email": "editor@example.com",
                          "password": "editor-pass",
                          "role": "EDITOR"
                        }
                        """))
                .andExpect(status().isOk());

        Cookie editorSession = login("editor@example.com", "editor-pass");
        mockMvc.perform(get("/api/llm/settings").cookie(editorSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("Admin account session required")));

        MvcResult apiKeyResult = mockMvc.perform(post("/api/api-keys")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "automation",
                          "roles": ["ADMIN"]
                        }
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String token = extractJsonValue(apiKeyResult, "token");

        mockMvc.perform(get("/api/llm/settings").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("Admin account session required")));
    }

    private Cookie login(String identifier, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "identifier": "%s",
                          "password": "%s"
                        }
                        """.formatted(identifier, password)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie sessionCookie = result.getResponse().getCookie(AuthService.SESSION_COOKIE_NAME);
        assertNotNull(sessionCookie);
        return sessionCookie;
    }

    private String extractJsonValue(MvcResult result, String fieldName) throws Exception {
        String body = result.getResponse().getContentAsString();
        String marker = "\"" + fieldName + "\":\"";
        int startIndex = body.indexOf(marker);
        if (startIndex < 0) {
            throw new IllegalStateException("Field not found: " + fieldName + " in " + body);
        }
        int valueStart = startIndex + marker.length();
        int valueEnd = body.indexOf('"', valueStart);
        return body.substring(valueStart, valueEnd);
    }
}
