package dev.golemcore.brain.adapter.in.web;

import dev.golemcore.brain.application.port.out.LlmChatPort;
import dev.golemcore.brain.application.service.auth.AuthService;
import dev.golemcore.brain.domain.llm.LlmChatRequest;
import dev.golemcore.brain.domain.llm.LlmChatResponse;
import jakarta.servlet.http.Cookie;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SpaceChatControllerIntegrationTest {

    private static Path storageRoot;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        storageRoot = tempDir.resolve("space-chat-controller-integration-test");
        registry.add("brain.storage-root", () -> storageRoot.toString());
        registry.add("brain.seed-demo-content", () -> "false");
        registry.add("brain.auth-disabled", () -> "false");
        registry.add("brain.public-access", () -> "false");
        registry.add("brain.admin-username", () -> "admin");
        registry.add("brain.admin-email", () -> "admin@example.com");
        registry.add("brain.admin-password", () -> "admin");
    }

    private final MockMvc mockMvc;
    private final RecordingLlmChatPort chatPort;

    SpaceChatControllerIntegrationTest(MockMvc mockMvc, RecordingLlmChatPort chatPort) {
        this.mockMvc = mockMvc;
        this.chatPort = chatPort;
    }

    @BeforeEach
    void resetChatPort() {
        chatPort.reset();
    }

    @Test
    void shouldAnswerSpaceChatQuestionWithSourcePages() throws Exception {
        Cookie adminSession = login("admin", "admin");
        createSpace(adminSession, "engineering", "Engineering");
        createLlmProviderAndModel(adminSession);
        createPage(adminSession, "engineering", "", "Roadmap", "roadmap", "Launch roadmap and milestones.");
        createPage(adminSession, "engineering", "", "Vacation", "vacation", "Vacation policy.");
        chatPort.responses.add("Roadmap milestones are documented in Roadmap.");
        chatPort.responses.add("Updated compact summary");

        mockMvc.perform(post("/api/spaces/engineering/chat")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "message": "What does the roadmap say?",
                          "summary": "Earlier chat summary",
                          "turnCount": 6
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", is("Roadmap milestones are documented in Roadmap.")))
                .andExpect(jsonPath("$.summary", is("Updated compact summary")))
                .andExpect(jsonPath("$.compacted", is(true)))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].path", is("roadmap")));

        assertTrue(chatPort.requests.getFirst().getMessages().getFirst().getContent().contains("Earlier chat summary"));
        assertTrue(chatPort.requests.getFirst().getMessages().get(1).getContent().contains("Launch roadmap"));
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
        private final List<String> responses = new ArrayList<>();
        private String response = "OK";

        @Override
        public LlmChatResponse chat(LlmChatRequest request) {
            requests.add(request);
            String content = responses.isEmpty() ? response : responses.remove(0);
            return LlmChatResponse.builder()
                    .content(content)
                    .finishReason("stop")
                    .build();
        }

        void reset() {
            requests.clear();
            responses.clear();
            response = "OK";
        }
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

    private void createLlmProviderAndModel(Cookie adminSession) throws Exception {
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

        mockMvc.perform(post("/api/llm/models")
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
                .andExpect(status().isCreated());
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
}
