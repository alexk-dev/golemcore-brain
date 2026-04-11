package dev.golemcore.brain.application.service.dynamicapi;

import dev.golemcore.brain.application.port.out.DynamicSpaceApiRepository;
import dev.golemcore.brain.application.port.out.DynamicSpaceApiToolPort;
import dev.golemcore.brain.application.port.out.LlmChatPort;
import dev.golemcore.brain.application.port.out.LlmSettingsRepository;
import dev.golemcore.brain.application.port.out.SpaceRepository;
import dev.golemcore.brain.domain.Secret;
import dev.golemcore.brain.domain.auth.AuthContext;
import dev.golemcore.brain.domain.auth.SpaceMembership;
import dev.golemcore.brain.domain.auth.UserRole;
import dev.golemcore.brain.domain.dynamicapi.DynamicSpaceApiConfig;
import dev.golemcore.brain.domain.dynamicapi.DynamicSpaceApiRunResult;
import dev.golemcore.brain.domain.dynamicapi.DynamicSpaceApiSettings;
import dev.golemcore.brain.domain.llm.LlmApiType;
import dev.golemcore.brain.domain.llm.LlmChatRequest;
import dev.golemcore.brain.domain.llm.LlmChatResponse;
import dev.golemcore.brain.domain.llm.LlmModelConfig;
import dev.golemcore.brain.domain.llm.LlmModelKind;
import dev.golemcore.brain.domain.llm.LlmProviderConfig;
import dev.golemcore.brain.domain.llm.LlmSettings;
import dev.golemcore.brain.domain.llm.LlmToolCall;
import dev.golemcore.brain.domain.llm.LlmToolDefinition;
import dev.golemcore.brain.domain.llm.LlmToolResult;
import dev.golemcore.brain.domain.space.Space;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DynamicSpaceApiServiceTest {

    private static final String SPACE_ID = "space-1";
    private static final Space SPACE = Space.builder()
            .id(SPACE_ID)
            .slug("docs")
            .name("Docs")
            .createdAt(Instant.now())
            .build();

    @Test
    void shouldRunToolLoopAndReturnJsonResult() {
        InMemoryDynamicSpaceApiRepository apiRepository = new InMemoryDynamicSpaceApiRepository();
        ScriptedLlmChatPort chatPort = new ScriptedLlmChatPort();
        RecordingToolPort toolPort = new RecordingToolPort();
        DynamicSpaceApiService service = new DynamicSpaceApiService(
                new SingleSpaceRepository(),
                apiRepository,
                new FixedLlmSettingsRepository(),
                chatPort,
                toolPort,
                new JsonMapper());

        DynamicSpaceApiConfig api = service.createApi(adminContext(), "docs", DynamicSpaceApiService.SaveCommand
                .builder()
                .slug("knowledge-search")
                .name("Knowledge Search")
                .modelConfigId("chat-model")
                .systemPrompt("Search the space and answer as JSON.")
                .enabled(true)
                .maxIterations(4)
                .build());

        DynamicSpaceApiRunResult result = service.runApi(viewerContext(), "docs", api.getSlug(),
                Map.of("query", "roadmap"));

        assertEquals(2, result.getIterations());
        assertEquals(1, result.getToolCallCount());
        assertEquals(SPACE_ID, toolPort.spaceIds.getFirst());
        assertEquals("search_files", toolPort.toolNames.getFirst());
        Map<?, ?> parsed = assertInstanceOf(Map.class, result.getResult());
        assertEquals("Roadmap is in product/roadmap.md", parsed.get("answer"));
        assertEquals("chat-model", chatPort.requests.getFirst().getModel().getId());
    }

    private AuthContext adminContext() {
        return AuthContext.builder()
                .authenticated(true)
                .memberships(List.of(SpaceMembership.builder().role(UserRole.ADMIN).build()))
                .build();
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

    private static class InMemoryDynamicSpaceApiRepository implements DynamicSpaceApiRepository {
        private final Map<String, DynamicSpaceApiSettings> settings = new LinkedHashMap<>();

        @Override
        public DynamicSpaceApiSettings load(String spaceId) {
            return settings.getOrDefault(spaceId, DynamicSpaceApiSettings.builder().build());
        }

        @Override
        public DynamicSpaceApiSettings save(String spaceId, DynamicSpaceApiSettings value) {
            settings.put(spaceId, value);
            return value;
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

    private static class ScriptedLlmChatPort implements LlmChatPort {
        private final List<LlmChatRequest> requests = new ArrayList<>();

        @Override
        public LlmChatResponse chat(LlmChatRequest request) {
            requests.add(request);
            if (requests.size() == 1) {
                return LlmChatResponse.builder()
                        .toolCalls(List.of(LlmToolCall.builder()
                                .id("call-1")
                                .name("search_files")
                                .arguments(Map.of("query", "roadmap"))
                                .build()))
                        .finishReason("tool_calls")
                        .build();
            }
            return LlmChatResponse.builder()
                    .content("{\"answer\":\"Roadmap is in product/roadmap.md\"}")
                    .finishReason("stop")
                    .build();
        }
    }

    private static class RecordingToolPort implements DynamicSpaceApiToolPort {
        private final List<String> spaceIds = new ArrayList<>();
        private final List<String> toolNames = new ArrayList<>();

        @Override
        public List<LlmToolDefinition> definitions() {
            return List.of(LlmToolDefinition.builder()
                    .name("search_files")
                    .description("Search files")
                    .inputSchema(Map.of("type", "object"))
                    .build());
        }

        @Override
        public LlmToolResult execute(String spaceId, LlmToolCall toolCall) {
            spaceIds.add(spaceId);
            toolNames.add(toolCall.getName());
            return LlmToolResult.success("Found roadmap", Map.of("matches", List.of("product/roadmap.md")));
        }
    }
}
