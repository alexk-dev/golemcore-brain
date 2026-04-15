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

package me.golemcore.brain.application.service.chat;

import me.golemcore.brain.application.port.out.LlmChatPort;
import me.golemcore.brain.application.port.out.LlmSettingsRepository;
import me.golemcore.brain.application.port.out.SpaceRepository;
import me.golemcore.brain.application.port.out.WikiDocumentCatalogPort;
import me.golemcore.brain.domain.Secret;
import me.golemcore.brain.domain.WikiIndexedDocument;
import me.golemcore.brain.domain.WikiNodeKind;
import me.golemcore.brain.domain.auth.AuthContext;
import me.golemcore.brain.domain.auth.SpaceMembership;
import me.golemcore.brain.domain.auth.UserRole;
import me.golemcore.brain.domain.llm.LlmApiType;
import me.golemcore.brain.domain.llm.LlmChatRequest;
import me.golemcore.brain.domain.llm.LlmChatResponse;
import me.golemcore.brain.domain.llm.LlmModelConfig;
import me.golemcore.brain.domain.llm.LlmModelKind;
import me.golemcore.brain.domain.llm.LlmProviderConfig;
import me.golemcore.brain.domain.llm.LlmSettings;
import me.golemcore.brain.domain.space.Space;
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

    @Test
    void shouldUseMostRecentHistoryWhenBuildingContext() {
        RecordingLlmChatPort chatPort = new RecordingLlmChatPort();
        SpaceChatService service = new SpaceChatService(
                new SingleSpaceRepository(),
                new FixedDocumentCatalog(),
                new FixedLlmSettingsRepository(),
                chatPort);
        List<SpaceChatService.ChatMessage> history = new ArrayList<>();
        for (int index = 1; index <= 13; index++) {
            history.add(SpaceChatService.ChatMessage.builder()
                    .role("user")
                    .content("recent-message-" + index)
                    .build());
        }

        service.chat(viewerContext(), "docs", SpaceChatService.ChatCommand.builder()
                .message("What is the product roadmap?")
                .history(history)
                .build());

        List<String> requestContents = chatPort.requests.getFirst().getMessages().stream()
                .map(message -> message.getContent())
                .toList();
        assertFalse(requestContents.stream().anyMatch("recent-message-1"::equals));
        assertTrue(requestContents.stream().anyMatch("recent-message-13"::equals));
    }

    @Test
    void shouldUseMostRecentHistoryWhenCompactingSummary() {
        RecordingLlmChatPort chatPort = new RecordingLlmChatPort();
        chatPort.respondWith("Answer before summary.", "Recent compact summary.");
        SpaceChatService service = new SpaceChatService(
                new SingleSpaceRepository(),
                new FixedDocumentCatalog(),
                new FixedLlmSettingsRepository(),
                chatPort);
        List<SpaceChatService.ChatMessage> history = new ArrayList<>();
        for (int index = 1; index <= 13; index++) {
            history.add(SpaceChatService.ChatMessage.builder()
                    .role("assistant")
                    .content("compact-message-" + index)
                    .build());
        }

        service.chat(viewerContext(), "docs", SpaceChatService.ChatCommand.builder()
                .message("What is the product roadmap?")
                .turnCount(6)
                .history(history)
                .build());

        String summaryPrompt = chatPort.requests.get(1).getMessages().getFirst().getContent();
        assertFalse(summaryPrompt.lines().anyMatch(line -> line.endsWith("compact-message-1")));
        assertTrue(summaryPrompt.lines().anyMatch(line -> line.endsWith("compact-message-13")));
    }

    @Test
    void shouldHandleDocumentsWithoutPathsWhenBuildingSources() {
        RecordingLlmChatPort chatPort = new RecordingLlmChatPort();
        SpaceChatService service = new SpaceChatService(
                new SingleSpaceRepository(),
                new NullPathDocumentCatalog(),
                new FixedLlmSettingsRepository(),
                chatPort);

        SpaceChatResponse response = service.chat(viewerContext(), "docs", SpaceChatService.ChatCommand.builder()
                .message("What is the roadmap?")
                .build());

        assertEquals("", response.getSources().getFirst().getPath());
        assertTrue(response.getSources().stream().allMatch(source -> source.getPath() != null));
        assertFalse(chatPort.requests.getFirst().getMessages().getFirst().getContent().contains("Path: null"));
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

    private static class NullPathDocumentCatalog implements WikiDocumentCatalogPort {
        @Override
        public List<WikiIndexedDocument> listDocuments(String spaceId) {
            return List.of(
                    document(null, null, "Roadmap", "Roadmap details."),
                    document("doc-2", "roadmap-notes", "Roadmap Notes", "Roadmap details."));
        }

        private WikiIndexedDocument document(String id, String path, String title, String body) {
            return WikiIndexedDocument.builder()
                    .id(id)
                    .path(path)
                    .parentPath("")
                    .title(title)
                    .body(body)
                    .kind(WikiNodeKind.PAGE)
                    .updatedAt(Instant.now())
                    .revision("rev-" + id)
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
