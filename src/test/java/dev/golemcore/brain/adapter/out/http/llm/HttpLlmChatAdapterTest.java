package dev.golemcore.brain.adapter.out.http.llm;

import dev.golemcore.brain.domain.Secret;
import dev.golemcore.brain.domain.llm.LlmApiType;
import dev.golemcore.brain.domain.llm.LlmChatMessage;
import dev.golemcore.brain.domain.llm.LlmChatRequest;
import dev.golemcore.brain.domain.llm.LlmChatResponse;
import dev.golemcore.brain.domain.llm.LlmModelConfig;
import dev.golemcore.brain.domain.llm.LlmModelKind;
import dev.golemcore.brain.domain.llm.LlmProviderConfig;
import dev.golemcore.brain.domain.llm.LlmReasoningEffort;
import dev.golemcore.brain.domain.llm.LlmToolCall;
import dev.golemcore.brain.domain.llm.LlmToolDefinition;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpLlmChatAdapterTest {

    private TestServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void shouldUseResponsesEndpointForOpenAiChatByDefault() throws Exception {
        server = TestServer.start("""
                {
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {"type": "output_text", "text": "Hello from responses"}
                      ]
                    }
                  ],
                  "status": "completed"
                }
                """);
        HttpLlmChatAdapter adapter = new HttpLlmChatAdapter(new ObjectMapper());

        LlmChatResponse response = adapter.chat(LlmChatRequest.builder()
                .provider(provider(false))
                .model(LlmModelConfig.builder()
                        .provider("openai")
                        .modelId("gpt-5.4")
                        .kind(LlmModelKind.CHAT)
                        .supportsTemperature(false)
                        .reasoningEffort(LlmReasoningEffort.HIGH)
                        .build())
                .systemPrompt("Be precise.")
                .messages(List.of(LlmChatMessage.builder().role("user").content("Hello").build()))
                .tools(List.of(LlmToolDefinition.builder()
                        .name("search_files")
                        .description("Search files")
                        .inputSchema(Map.of("type", "object"))
                        .build()))
                .build());

        assertEquals("Hello from responses", response.getContent());
        assertEquals("/v1/responses", server.path());
        assertTrue(server.requestBody().contains("\"input\""));
        assertTrue(server.requestBody().contains("\"instructions\":\"Be precise."));
        assertTrue(server.requestBody().contains("\"reasoning\""));
        assertTrue(server.requestBody().contains("\"effort\":\"high\""));
        assertTrue(server.requestBody().contains("\"tools\""));
        assertFalse(server.requestBody().contains("\"temperature\""));
    }

    @Test
    void shouldParseResponsesFunctionCalls() throws Exception {
        server = TestServer.start("""
                {
                  "output": [
                    {
                      "type": "function_call",
                      "call_id": "call-1",
                      "name": "search_files",
                      "arguments": "{\\\"query\\\":\\\"roadmap\\\"}"
                    }
                  ],
                  "status": "requires_action"
                }
                """);
        HttpLlmChatAdapter adapter = new HttpLlmChatAdapter(new ObjectMapper());

        LlmChatResponse response = adapter.chat(LlmChatRequest.builder()
                .provider(provider(false))
                .model(LlmModelConfig.builder()
                        .provider("openai")
                        .modelId("gpt-5.4")
                        .kind(LlmModelKind.CHAT)
                        .build())
                .messages(List.of(LlmChatMessage.builder().role("user").content("Find roadmap").build()))
                .build());

        assertEquals("/v1/responses", server.path());
        assertEquals(1, response.getToolCalls().size());
        assertEquals("call-1", response.getToolCalls().getFirst().getId());
        assertEquals("search_files", response.getToolCalls().getFirst().getName());
        assertEquals("roadmap", response.getToolCalls().getFirst().getArguments().get("query"));
    }

    @Test
    void shouldIncludeFunctionCallOutputsInResponsesInput() throws Exception {
        server = TestServer.start("""
                {
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {"type": "output_text", "text": "Done"}
                      ]
                    }
                  ],
                  "status": "completed"
                }
                """);
        HttpLlmChatAdapter adapter = new HttpLlmChatAdapter(new ObjectMapper());

        LlmChatResponse response = adapter.chat(LlmChatRequest.builder()
                .provider(provider(false))
                .model(LlmModelConfig.builder()
                        .provider("openai")
                        .modelId("gpt-5.4")
                        .kind(LlmModelKind.CHAT)
                        .build())
                .messages(List.of(
                        LlmChatMessage.builder().role("user").content("Find roadmap").build(),
                        LlmChatMessage.builder()
                                .role("assistant")
                                .toolCalls(List.of(LlmToolCall.builder()
                                        .id("call-1")
                                        .name("search_files")
                                        .arguments(Map.of("query", "roadmap"))
                                        .build()))
                                .build(),
                        LlmChatMessage.builder()
                                .role("tool")
                                .toolCallId("call-1")
                                .content("Found roadmap")
                                .build()))
                .build());

        assertEquals("Done", response.getContent());
        assertEquals("/v1/responses", server.path());
        assertTrue(server.requestBody().contains("\"type\":\"function_call\""));
        assertTrue(server.requestBody().contains("\"type\":\"function_call_output\""));
        assertTrue(server.requestBody().contains("\"call_id\":\"call-1\""));
        assertTrue(server.requestBody().contains("\"output\":\"Found roadmap\""));
    }

    @Test
    void shouldUseLegacyChatCompletionsWhenProviderRequestsLegacyApi() throws Exception {
        server = TestServer.start("""
                {
                  "choices": [
                    {"message": {"content": "Hello legacy"}, "finish_reason": "stop"}
                  ]
                }
                """);
        HttpLlmChatAdapter adapter = new HttpLlmChatAdapter(new ObjectMapper());

        LlmChatResponse response = adapter.chat(LlmChatRequest.builder()
                .provider(provider(true))
                .model(LlmModelConfig.builder()
                        .provider("openai")
                        .modelId("gpt-4o")
                        .kind(LlmModelKind.CHAT)
                        .temperature(0.2d)
                        .build())
                .messages(List.of(LlmChatMessage.builder().role("user").content("Hello").build()))
                .build());

        assertEquals("Hello legacy", response.getContent());
        assertEquals("/v1/chat/completions", server.path());
        assertTrue(server.requestBody().contains("\"messages\""));
        assertTrue(server.requestBody().contains("\"temperature\":0.2"));
    }

    private LlmProviderConfig provider(Boolean legacyApi) {
        return LlmProviderConfig.builder()
                .apiKey(Secret.of("sk-test"))
                .apiType(LlmApiType.OPENAI)
                .baseUrl(server.baseUrl())
                .legacyApi(legacyApi)
                .requestTimeoutSeconds(30)
                .build();
    }

    @SuppressWarnings({ "PMD.TestClassWithoutTestCases", "PMD.AvoidFieldNameMatchingMethodName", "PMD.CloseResource" })
    private static class TestServer {
        private final HttpServer server;
        private final ExecutorService executor;
        private volatile String capturedRequestBody;
        private volatile String capturedPath;

        private TestServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static TestServer start(String responseBody) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            TestServer testServer = new TestServer(server, executor);
            server.createContext("/", exchange -> testServer.handle(exchange, responseBody));
            server.setExecutor(executor);
            server.start();
            return testServer;
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        }

        String requestBody() {
            return capturedRequestBody;
        }

        String path() {
            return capturedPath;
        }

        void stop() {
            server.stop(0);
            executor.shutdownNow();
        }

        private void handle(HttpExchange exchange, String responseBody) throws IOException {
            capturedPath = exchange.getRequestURI().getPath();
            capturedRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }
}
