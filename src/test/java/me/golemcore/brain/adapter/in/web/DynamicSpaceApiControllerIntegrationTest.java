/*
 * Copyright 2026 Aleksei Kuleshov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contact: alex@kuleshov.tech
 */

package me.golemcore.brain.adapter.in.web;

import me.golemcore.brain.application.port.out.LlmChatPort;
import me.golemcore.brain.application.service.auth.AuthService;
import me.golemcore.brain.domain.llm.LlmChatMessage;
import me.golemcore.brain.domain.llm.LlmChatRequest;
import me.golemcore.brain.domain.llm.LlmChatResponse;
import me.golemcore.brain.domain.llm.LlmToolCall;
import jakarta.servlet.http.Cookie;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class DynamicSpaceApiControllerIntegrationTest {

    private static Path storageRoot;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        storageRoot = tempDir.resolve("dynamic-space-api-controller-integration-test");
        registry.add("brain.storage-root", () -> storageRoot.toString());
        registry.add("brain.seed-demo-content", () -> "false");
        registry.add("brain.auth-disabled", () -> "false");
        registry.add("brain.public-access", () -> "false");
        registry.add("brain.admin-username", () -> "admin");
        registry.add("brain.admin-email", () -> "admin@example.com");
        registry.add("brain.admin-password", () -> "admin");
    }

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final RecordingLlmChatPort chatPort;

    DynamicSpaceApiControllerIntegrationTest(
            MockMvc mockMvc,
            ObjectMapper objectMapper,
            RecordingLlmChatPort chatPort) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.chatPort = chatPort;
    }

    @BeforeEach
    void resetChatPort() {
        chatPort.reset();
    }

    @Test
    void shouldManageAndRunDynamicApiWithSpaceScopedFilesystemTools() throws Exception {
        Cookie adminSession = login("admin", "admin");
        createSpace(adminSession, "engineering", "Engineering");
        createSpace(adminSession, "support", "Support");
        String modelConfigId = createLlmProviderAndModel(adminSession);
        createPage(adminSession, "engineering", "", "Engineering Guide", "guide", "Engineering alpha knowledge");
        createPage(adminSession, "support", "", "Support Guide", "guide", "Support beta knowledge");

        MvcResult createResult = mockMvc.perform(post("/api/spaces/engineering/dynamic-apis")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "slug": "knowledge-search",
                          "name": "Knowledge Search",
                          "description": "Search active space docs",
                          "modelConfigId": "%s",
                          "systemPrompt": "Use tools to inspect wiki files.",
                          "enabled": true,
                          "maxIterations": 4
                        }
                        """.formatted(modelConfigId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug", is("knowledge-search")))
                .andExpect(jsonPath("$.name", is("Knowledge Search")))
                .andReturn();
        String apiId = extractString(createResult, "id");

        mockMvc.perform(get("/api/spaces/support/dynamic-apis").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/spaces/engineering/dynamic-apis").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].slug", is("knowledge-search")));

        chatPort.script(List.of(
                LlmChatResponse.builder()
                        .toolCalls(List.of(LlmToolCall.builder()
                                .id("call-1")
                                .name("search_files")
                                .arguments(Map.of("query", "alpha", "maxResults", 5))
                                .build()))
                        .finishReason("tool_calls")
                        .build(),
                LlmChatResponse.builder()
                        .content("{\"answer\":\"Engineering alpha knowledge found\"}")
                        .finishReason("stop")
                        .build()));

        mockMvc.perform(post("/api/spaces/engineering/dynamic-apis/knowledge-search/run")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "query": "alpha"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiSlug", is("knowledge-search")))
                .andExpect(jsonPath("$.result.answer", is("Engineering alpha knowledge found")))
                .andExpect(jsonPath("$.iterations", is(2)))
                .andExpect(jsonPath("$.toolCallCount", is(1)));

        assertEquals(2, chatPort.requests.size());
        assertTrue(chatPort.requests.getFirst().getSystemPrompt().contains("Use tools to inspect wiki files."));
        String toolMessage = chatPort.requests.get(1).getMessages().stream()
                .filter(message -> "tool".equals(message.getRole()))
                .map(LlmChatMessage::getContent)
                .findFirst()
                .orElseThrow();
        assertTrue(toolMessage.contains("Engineering alpha knowledge"));
        assertFalse(toolMessage.contains("Support beta knowledge"));

        mockMvc.perform(put("/api/spaces/engineering/dynamic-apis/{apiId}", apiId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "slug": "knowledge-search",
                          "name": "Knowledge Search",
                          "description": "Disabled for maintenance",
                          "modelConfigId": "%s",
                          "systemPrompt": "Use tools to inspect wiki files.",
                          "enabled": false,
                          "maxIterations": 4
                        }
                        """.formatted(modelConfigId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(false)));

        mockMvc.perform(post("/api/spaces/engineering/dynamic-apis/knowledge-search/run")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Dynamic API is disabled")));

        mockMvc.perform(delete("/api/spaces/engineering/dynamic-apis/{apiId}", apiId)
                .cookie(adminSession))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/spaces/engineering/dynamic-apis").cookie(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @TestConfiguration
    static class LlmPortConfiguration {
        @Bean
        @Primary
        RecordingLlmChatPort recordingLlmChatPort() {
            return new RecordingLlmChatPort();
        }
    }

    static class RecordingLlmChatPort implements LlmChatPort {
        private final List<LlmChatRequest> requests = new ArrayList<>();
        private List<LlmChatResponse> scriptedResponses = List.of();

        @Override
        public LlmChatResponse chat(LlmChatRequest request) {
            requests.add(request);
            if (requests.size() > scriptedResponses.size()) {
                throw new IllegalStateException("No scripted LLM response for request " + requests.size());
            }
            return scriptedResponses.get(requests.size() - 1);
        }

        void script(List<LlmChatResponse> responses) {
            scriptedResponses = List.copyOf(responses);
        }

        void reset() {
            requests.clear();
            scriptedResponses = List.of();
        }
    }

    private String createLlmProviderAndModel(Cookie adminSession) throws Exception {
        mockMvc.perform(post("/api/llm/providers")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "openai",
                          "apiType": "openai",
                          "baseUrl": "http://llm.test/v1",
                          "requestTimeoutSeconds": 30,
                          "apiKey": {
                            "value": "sk-test",
                            "encrypted": false
                          }
                        }
                        """))
                .andExpect(status().isCreated());

        MvcResult modelResult = mockMvc.perform(post("/api/llm/models")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "provider": "openai",
                          "modelId": "gpt-test",
                          "displayName": "Test Chat",
                          "kind": "chat",
                          "enabled": true
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.models[0].modelId", is("gpt-test")))
                .andReturn();
        return extractFirstModelId(modelResult);
    }

    private void createSpace(Cookie adminSession, String slug, String name) throws Exception {
        mockMvc.perform(post("/api/spaces")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "slug": "%s",
                          "name": "%s"
                        }
                        """.formatted(slug, name)))
                .andExpect(status().isCreated());
    }

    private void createPage(Cookie adminSession, String spaceSlug, String parentPath, String title, String slug,
            String content) throws Exception {
        mockMvc.perform(post("/api/spaces/{spaceSlug}/pages", spaceSlug)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "parentPath": "%s",
                          "title": "%s",
                          "slug": "%s",
                          "content": "%s",
                          "kind": "PAGE"
                        }
                        """.formatted(parentPath, title, slug, content)))
                .andExpect(status().isOk());
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

    @SuppressWarnings("unchecked")
    private String extractFirstModelId(MvcResult result) throws Exception {
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        List<Map<String, Object>> models = (List<Map<String, Object>>) body.get("models");
        return String.valueOf(models.getFirst().get("id"));
    }

    @SuppressWarnings("unchecked")
    private String extractString(MvcResult result, String fieldName) throws Exception {
        Map<String, Object> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        return String.valueOf(body.get(fieldName));
    }
}
