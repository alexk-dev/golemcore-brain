package dev.golemcore.brain.application.service.chat;

import dev.golemcore.brain.application.port.out.LlmChatPort;
import dev.golemcore.brain.application.port.out.LlmSettingsRepository;
import dev.golemcore.brain.application.port.out.SpaceRepository;
import dev.golemcore.brain.application.port.out.WikiDocumentCatalogPort;
import dev.golemcore.brain.domain.Secret;
import dev.golemcore.brain.domain.WikiIndexedDocument;
import dev.golemcore.brain.domain.WikiNodeKind;
import dev.golemcore.brain.domain.auth.AuthContext;
import dev.golemcore.brain.domain.auth.SpaceMembership;
import dev.golemcore.brain.domain.auth.UserRole;
import dev.golemcore.brain.domain.llm.LlmApiType;
import dev.golemcore.brain.domain.llm.LlmChatRequest;
import dev.golemcore.brain.domain.llm.LlmChatResponse;
import dev.golemcore.brain.domain.llm.LlmModelConfig;
import dev.golemcore.brain.domain.llm.LlmModelKind;
import dev.golemcore.brain.domain.llm.LlmProviderConfig;
import dev.golemcore.brain.domain.llm.LlmSettings;
import dev.golemcore.brain.domain.space.Space;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaceChatServiceTest {

    private static final String SPACE_ID = "space-1";
    private static final Space SPACE = Space.builder()
            .id(SPACE_ID)
            .slug("docs")
            .name("Docs")
            .createdAt(Instant.now())
            .build();

    @Test
    void shouldAnswerUsingRelevantSpaceDocumentsAsContext() {
        RecordingLlmChatPort chatPort = new RecordingLlmChatPort();
        SpaceChatService service = new SpaceChatService(
                new SingleSpaceRepository(),
                new FixedDocumentCatalog(),
                new FixedLlmSettingsRepository(),
                chatPort);

        SpaceChatResponse response = service.chat(viewerContext(), "docs", SpaceChatService.ChatCommand.builder()
                .message("What is the product roadmap?")
                .build());

        assertEquals("The roadmap is in the product roadmap page.", response.getAnswer());
        assertEquals("chat-model", response.getModelConfigId());
        assertEquals("product/roadmap", response.getSources().getFirst().getPath());
        assertEquals("Product Roadmap", response.getSources().getFirst().getTitle());
        LlmChatRequest request = chatPort.requests.getFirst();
        assertEquals("gpt-test", request.getModel().getModelId());
        String prompt = request.getMessages().getFirst().getContent();
        assertTrue(prompt.contains("Product Roadmap"));
        assertTrue(prompt.contains("launch milestones"));
        assertFalse(prompt.contains("Vacation Policy"));
    }

    @Test
    void shouldCompactConversationSummaryOnPeriodicTurn() {
        RecordingLlmChatPort chatPort = new RecordingLlmChatPort();
        chatPort.respondWith("The roadmap update is ready.", "Compact roadmap summary.");
        SpaceChatService service = new SpaceChatService(
                new SingleSpaceRepository(),
                new FixedDocumentCatalog(),
                new FixedLlmSettingsRepository(),
                chatPort);

        SpaceChatResponse response = service.chat(viewerContext(), "docs", SpaceChatService.ChatCommand.builder()
                .message("What changed since then?")
                .summary("Earlier we discussed the Q2 roadmap.")
                .turnCount(6)
                .history(List.of(
                        SpaceChatService.ChatMessage.builder()
                                .role("user")
                                .content("What is the product roadmap?")
                                .build(),
                        SpaceChatService.ChatMessage.builder()
                                .role("assistant")
                                .content("The roadmap covers launch milestones.")
                                .build()))
                .build());

        assertEquals("The roadmap update is ready.", response.getAnswer());
        assertTrue(response.isCompacted());
        assertEquals("Compact roadmap summary.", response.getSummary());
        assertEquals(2, chatPort.requests.size());
        assertTrue(chatPort.requests.getFirst().getMessages().getFirst().getContent()
                .contains("Earlier we discussed the Q2 roadmap."));
        assertTrue(chatPort.requests.getFirst().getMessages().getLast().getContent()
                .contains("Product Roadmap"));
        assertTrue(chatPort.requests.get(1).getMessages().getFirst().getContent()
                .contains("The roadmap update is ready."));
    }

    private AuthContext viewerContext() {
        return AuthContext.builder()
                .authenticated(true)
                .memberships(List.of(SpaceMembership.builder().spaceId(SPACE_ID).role(UserRole.VIEWER).build()))
                .build();
    }

    private static class SingleSpaceRepository implements SpaceRepository {
        @Override
        public void initialize() {
        }

        @Override
        public List<Space> listSpaces() {
            return List.of(SPACE);
        }

        @Override
        public Optional<Space> findById(String id) {
            return SPACE_ID.equals(id) ? Optional.of(SPACE) : Optional.empty();
        }

        @Override
        public Optional<Space> findBySlug(String slug) {
            return SPACE.getSlug().equals(slug) ? Optional.of(SPACE) : Optional.empty();
        }

        @Override
        public Space save(Space space) {
            return space;
        }

        @Override
        public void delete(String id) {
        }
    }

    private static class FixedDocumentCatalog implements WikiDocumentCatalogPort {
        @Override
        public List<WikiIndexedDocument> listDocuments(String spaceId) {
            return List.of(
                    document("product/roadmap", "Product Roadmap", "Q2 launch milestones and product roadmap."),
                    document("people/vacation", "Vacation Policy", "Time off policy and holiday calendar."));
        }

        private WikiIndexedDocument document(String path, String title, String body) {
            return WikiIndexedDocument.builder()
                    .id(path)
                    .path(path)
                    .parentPath(path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : "")
                    .title(title)
                    .body(body)
                    .kind(WikiNodeKind.PAGE)
                    .updatedAt(Instant.now())
                    .revision("rev-" + path)
                    .build();
        }
    }

    private static class FixedLlmSettingsRepository implements LlmSettingsRepository {
        private final LlmSettings settings = LlmSettings.builder()
                .providers(new LinkedHashMap<>(Map.of("openai", LlmProviderConfig.builder()
                        .apiKey(Secret.of("sk-test"))
                        .apiType(LlmApiType.OPENAI)
                        .requestTimeoutSeconds(30)
                        .build())))
                .models(new ArrayList<>(List.of(LlmModelConfig.builder()
                        .id("chat-model")
                        .provider("openai")
                        .modelId("gpt-test")
                        .kind(LlmModelKind.CHAT)
                        .enabled(true)
                        .build())))
                .build();

        @Override
        public void initialize() {
        }

        @Override
        public LlmSettings load() {
            return settings;
        }

        @Override
        public LlmSettings save(LlmSettings settings) {
            return settings;
        }
    }

    private static class RecordingLlmChatPort implements LlmChatPort {
        private final List<LlmChatRequest> requests = new ArrayList<>();
        private final List<String> responses = new ArrayList<>();

        @Override
        public LlmChatResponse chat(LlmChatRequest request) {
            requests.add(request);
            String content = responses.isEmpty() ? "The roadmap is in the product roadmap page." : responses.remove(0);
            return LlmChatResponse.builder()
                    .content(content)
                    .finishReason("stop")
                    .build();
        }

        void respondWith(String... contents) {
            responses.addAll(List.of(contents));
        }
    }
}
