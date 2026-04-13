package dev.golemcore.brain.adapter.out.http.llm;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.golemcore.brain.domain.Secret;
import dev.golemcore.brain.domain.llm.LlmApiType;
import dev.golemcore.brain.domain.llm.LlmChatMessage;
import dev.golemcore.brain.domain.llm.LlmChatRequest;
import dev.golemcore.brain.domain.llm.LlmChatResponse;
import dev.golemcore.brain.domain.llm.LlmEmbeddingRequest;
import dev.golemcore.brain.domain.llm.LlmEmbeddingResponse;
import dev.golemcore.brain.domain.llm.LlmModelConfig;
import dev.golemcore.brain.domain.llm.LlmModelKind;
import dev.golemcore.brain.domain.llm.LlmProviderConfig;
import dev.golemcore.brain.domain.llm.LlmToolCall;
import dev.golemcore.brain.domain.llm.LlmToolDefinition;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Langchain4jLlmAdapterTest {

    private TestServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void shouldUseResponsesEndpointForOpenAiChatByDefault() throws Exception {
        server = TestServer.start(sse(
                event("response.output_text.delta", """
                        {
                          "type": "response.output_text.delta",
                          "delta": "Hello from responses",
                          "output_index": 0
                        }
                        """),
                event("response.completed", """
                        {
                          "type": "response.completed",
                          "response": {
                            "id": "resp-test",
                            "object": "response",
                            "created_at": 1775521153,
                            "status": "completed",
                            "model": "gpt-5.4",
                            "output": [],
                            "usage": {
                              "input_tokens": 10,
                              "output_tokens": 5,
                              "total_tokens": 15
                            }
                          }
                        }
                        """)), "text/event-stream");
        Langchain4jLlmAdapter adapter = new Langchain4jLlmAdapter(new ObjectMapper());

        LlmChatResponse response = adapter.chat(LlmChatRequest.builder()
                .provider(provider(false))
                .model(LlmModelConfig.builder()
                        .provider("openai")
                        .modelId("gpt-5.4")
                        .kind(LlmModelKind.CHAT)
                        .supportsTemperature(false)
                        .reasoningEffort("minimal")
                        .build())
                .systemPrompt("Be precise.")
                .messages(List.of(LlmChatMessage.builder().role("user").content("Hello").build()))
                .tools(List.of(LlmToolDefinition.builder()
                        .name("search_files")
                        .description("Search files")
                        .inputSchema(Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "query", Map.of("type", "string")),
                                "required", List.of("query")))
                        .build()))
                .build());

        assertEquals("Hello from responses", response.getContent());
        assertEquals("/v1/responses", server.path());
        assertTrue(server.requestBody().contains("\"stream\":true"));
        assertTrue(server.requestBody().contains("\"model\":\"gpt-5.4\""));
        assertTrue(server.requestBody().contains("\"effort\":\"minimal\""));
        assertTrue(server.requestBody().contains("\"tools\""));
        assertFalse(server.requestBody().contains("\"temperature\""));
    }

    @Test
    void shouldParseResponsesFunctionCalls() throws Exception {
        server = TestServer.start(sse(
                event("response.output_item.added", """
                        {
                          "type": "response.output_item.added",
                          "item": {
                            "id": "fc-test",
                            "type": "function_call",
                            "call_id": "call-1",
                            "name": "search_files"
                          },
                          "output_index": 0
                        }
                        """),
                event("response.function_call_arguments.done", """
                        {
                          "type": "response.function_call_arguments.done",
                          "item_id": "fc-test",
                          "arguments": "{\\"query\\":\\"roadmap\\"}"
                        }
                        """),
                event("response.completed", """
                        {
                          "type": "response.completed",
                          "response": {
                            "id": "resp-tool",
                            "object": "response",
                            "created_at": 1775521153,
                            "status": "completed",
                            "model": "gpt-5.4",
                            "output": [],
                            "usage": {
                              "input_tokens": 10,
                              "output_tokens": 5,
                              "total_tokens": 15
                            }
                          }
                        }
                        """)), "text/event-stream");
        Langchain4jLlmAdapter adapter = new Langchain4jLlmAdapter(new ObjectMapper());

        LlmChatResponse response = adapter.chat(LlmChatRequest.builder()
                .provider(provider(false))
                .model(LlmModelConfig.builder()
                        .provider("openai")
                        .modelId("gpt-5.4")
                        .kind(LlmModelKind.CHAT)
                        .build())
                .messages(List.of(LlmChatMessage.builder().role("user").content("Find roadmap").build()))
                .tools(List.of(searchFilesTool()))
                .build());

        assertEquals("/v1/responses", server.path());
        assertEquals(1, response.getToolCalls().size());
        assertEquals("call-1", response.getToolCalls().getFirst().getId());
        assertEquals("search_files", response.getToolCalls().getFirst().getName());
        assertEquals("roadmap", response.getToolCalls().getFirst().getArguments().get("query"));
    }

    @Test
    void shouldIncludeFunctionCallOutputsInResponsesInput() throws Exception {
        server = TestServer.start(sse(
                event("response.output_text.delta", """
                        {
                          "type": "response.output_text.delta",
                          "delta": "Done",
                          "output_index": 0
                        }
                        """),
                event("response.completed", """
                        {
                          "type": "response.completed",
                          "response": {
                            "id": "resp-done",
                            "object": "response",
                            "created_at": 1775521153,
                            "status": "completed",
                            "model": "gpt-5.4",
                            "output": []
                          }
                        }
                        """)), "text/event-stream");
        Langchain4jLlmAdapter adapter = new Langchain4jLlmAdapter(new ObjectMapper());

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
                                .toolName("search_files")
                                .content("Found roadmap")
                                .build()))
                .tools(List.of(searchFilesTool()))
                .build());

        assertEquals("Done", response.getContent());
        assertEquals("/v1/responses", server.path());
        assertTrue(server.requestBody().contains("\"type\":\"function_call\""));
        assertTrue(server.requestBody().contains("\"type\":\"function_call_output\""));
        assertTrue(server.requestBody().contains("\"call_id\":\"call-1\""));
        assertTrue(server.requestBody().contains("\"output\":\"Found roadmap\""));
    }

    @Test
    void shouldUseLangchain4jForEmbeddings() throws Exception {
        server = TestServer.start("""
                {
                  "object": "list",
                  "data": [
                    {
                      "object": "embedding",
                      "index": 0,
                      "embedding": [0.125, 0.25]
                    }
                  ],
                  "model": "text-embedding-3-small",
                  "usage": {
                    "prompt_tokens": 2,
                    "total_tokens": 2
                  }
                }
                """, "application/json");
        Langchain4jLlmAdapter adapter = new Langchain4jLlmAdapter(new ObjectMapper());

        LlmEmbeddingResponse response = adapter.embed(LlmEmbeddingRequest.builder()
                .provider(provider(false))
                .model(LlmModelConfig.builder()
                        .provider("openai")
                        .modelId("text-embedding-3-small")
                        .kind(LlmModelKind.EMBEDDING)
                        .dimensions(2)
                        .build())
                .inputs(List.of("hello"))
                .build());

        assertEquals(List.of(List.of(0.125d, 0.25d)), response.getEmbeddings());
        assertEquals("/v1/embeddings", server.path());
        assertTrue(server.requestBody().matches("(?s).*\"model\"\\s*:\\s*\"text-embedding-3-small\".*"),
                server.requestBody());
        assertTrue(server.requestBody().contains("\"input\""));
        assertTrue(server.requestBody().matches("(?s).*\"dimensions\"\\s*:\\s*2.*"), server.requestBody());
    }

    @Test
    void shouldUseLegacyChatCompletionsWhenProviderRequestsLegacyApi() throws Exception {
        server = TestServer.start("""
                {
                  "id": "chatcmpl-test",
                  "object": "chat.completion",
                  "created": 1775521153,
                  "model": "gpt-4o",
                  "choices": [
                    {
                      "index": 0,
                      "message": {"role": "assistant", "content": "Hello legacy"},
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 3,
                    "completion_tokens": 2,
                    "total_tokens": 5
                  }
                }
                """, "application/json");
        Langchain4jLlmAdapter adapter = new Langchain4jLlmAdapter(new ObjectMapper());

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
        assertTrue(server.requestBody().matches("(?s).*\"temperature\"\\s*:\\s*0\\.2.*"), server.requestBody());
    }

    private LlmToolDefinition searchFilesTool() {
        return LlmToolDefinition.builder()
                .name("search_files")
                .description("Search files")
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of(
                                        "type", "string",
                                        "description", "Search query")),
                        "required", List.of("query")))
                .build();
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

    private static String event(String name, String data) {
        return "event: " + name + "\n"
                + "data: " + data.replace("\n", "\ndata: ") + "\n\n";
    }

    private static String sse(String... events) {
        return String.join("", events);
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

        static TestServer start(String responseBody, String contentType) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            TestServer testServer = new TestServer(server, executor);
            server.createContext("/", exchange -> testServer.handle(exchange, responseBody, contentType));
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

        private void handle(HttpExchange exchange, String responseBody, String contentType) throws IOException {
            capturedPath = exchange.getRequestURI().getPath();
            capturedRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }
}
