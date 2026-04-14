package dev.golemcore.brain.adapter.out.http.llm;

import com.sun.net.httpserver.Headers;
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
                event("response.output_item.done", """
                        {
                          "type": "response.output_item.done",
                          "item": {
                            "id": "fc-test",
                            "type": "function_call",
                            "call_id": "call-1",
                            "name": "search_files",
                            "arguments": "{\\\"query\\\":\\\"roadmap\\\"}"
                          },
                          "output_index": 0
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
    void shouldUseNativeAnthropicMessagesEndpointForAnthropicChat() throws Exception {
        server = TestServer.start("""
                {
                  "id": "msg-test",
                  "type": "message",
                  "role": "assistant",
                  "model": "claude-3-5-sonnet-latest",
                  "content": [{"type": "text", "text": "Hello from Claude"}],
                  "stop_reason": "end_turn",
                  "usage": {"input_tokens": 4, "output_tokens": 3}
                }
                """, "application/json");
        Langchain4jLlmAdapter adapter = new Langchain4jLlmAdapter(new ObjectMapper());

        LlmChatResponse response = adapter.chat(LlmChatRequest.builder()
                .provider(provider(LlmApiType.ANTHROPIC, null))
                .model(LlmModelConfig.builder()
                        .provider("anthropic")
                        .modelId("claude-3-5-sonnet-latest")
                        .kind(LlmModelKind.CHAT)
                        .temperature(0.2d)
                        .build())
                .systemPrompt("Be concise.")
                .messages(List.of(LlmChatMessage.builder().role("user").content("Hello").build()))
                .tools(List.of(searchFilesTool()))
                .build());

        assertEquals("Hello from Claude", response.getContent());
        assertEquals("/v1/messages", server.path());
        assertEquals("sk-test", server.requestHeader("x-api-key"));
        assertEquals("2023-06-01", server.requestHeader("anthropic-version"));
        assertTrue(server.requestBody().contains("\"model\""));
        assertTrue(server.requestBody().contains("claude-3-5-sonnet-latest"));
        assertTrue(server.requestBody().contains("\"system\""));
        assertTrue(server.requestBody().contains("\"tools\""));
        assertTrue(server.requestBody().matches("(?s).*\"temperature\"\\s*:\\s*0\\.2.*"),
                server.requestBody());
    }

    @Test
    void shouldUseNativeGeminiGenerateContentEndpointForGeminiChat() throws Exception {
        server = TestServer.start("""
                {
                  "candidates": [
                    {
                      "content": {
                        "role": "model",
                        "parts": [{"text": "Hello from Gemini"}]
                      },
                      "finishReason": "STOP"
                    }
                  ],
                  "usageMetadata": {
                    "promptTokenCount": 3,
                    "candidatesTokenCount": 3,
                    "totalTokenCount": 6
                  },
                  "modelVersion": "gemini-1.5-flash"
                }
                """, "application/json");
        Langchain4jLlmAdapter adapter = new Langchain4jLlmAdapter(new ObjectMapper());

        LlmChatResponse response = adapter.chat(LlmChatRequest.builder()
                .provider(provider(LlmApiType.GEMINI, null))
                .model(LlmModelConfig.builder()
                        .provider("gemini")
                        .modelId("gemini-1.5-flash")
                        .kind(LlmModelKind.CHAT)
                        .supportsTemperature(false)
                        .reasoningEffort("medium")
                        .build())
                .systemPrompt("Be concise.")
                .messages(List.of(LlmChatMessage.builder().role("user").content("Hello").build()))
                .tools(List.of(searchFilesTool()))
                .build());

        assertEquals("Hello from Gemini", response.getContent());
        assertEquals("/v1beta/models/gemini-1.5-flash:generateContent", server.path());
        assertEquals("sk-test", server.requestHeader("x-goog-api-key"));
        assertTrue(server.requestBody().contains("\"systemInstruction\""));
        assertTrue(server.requestBody().contains("\"functionDeclarations\""));
        assertTrue(server.requestBody().contains("\"thinkingBudget\""));
        assertTrue(server.requestBody().contains("8192"));
        assertFalse(server.requestBody().contains("\"temperature\""));
    }

    @Test
    void shouldUseNativeGeminiEmbeddingEndpointForGeminiEmbeddings() throws Exception {
        server = TestServer.start("""
                {
                  "embeddings": [
                    {"values": [0.5, 0.75]}
                  ]
                }
                """, "application/json");
        Langchain4jLlmAdapter adapter = new Langchain4jLlmAdapter(new ObjectMapper());

        LlmEmbeddingResponse response = adapter.embed(LlmEmbeddingRequest.builder()
                .provider(provider(LlmApiType.GEMINI, null))
                .model(LlmModelConfig.builder()
                        .provider("gemini")
                        .modelId("text-embedding-004")
                        .kind(LlmModelKind.EMBEDDING)
                        .dimensions(2)
                        .build())
                .inputs(List.of("hello"))
                .build());

        assertEquals(List.of(List.of(0.5d, 0.75d)), response.getEmbeddings());
        assertEquals("/v1beta/models/text-embedding-004:batchEmbedContents", server.path());
        assertEquals("sk-test", server.requestHeader("x-goog-api-key"));
        assertTrue(server.requestBody().contains("\"outputDimensionality\""));
        assertTrue(server.requestBody().contains("2"));
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
        return provider(LlmApiType.OPENAI, legacyApi);
    }

    private LlmProviderConfig provider(LlmApiType apiType, Boolean legacyApi) {
        return LlmProviderConfig.builder()
                .apiKey(Secret.of("sk-test"))
                .apiType(apiType)
                .baseUrl(baseUrl(apiType))
                .legacyApi(legacyApi)
                .requestTimeoutSeconds(30)
                .build();
    }

    private String baseUrl(LlmApiType apiType) {
        return switch (apiType) {
        case ANTHROPIC, OPENAI -> server.baseUrl("v1");
        case GEMINI -> server.baseUrl("v1beta");
        };
    }

    private static String event(String name, String data) {
        return "event: " + name + "\n"
                + "data: " + data.replace("\n", "\ndata: ") + "\n\n";
    }

    private static String sse(String... events) {
        return String.join("", events);
    }

    @SuppressWarnings({ "PMD.TestClassWithoutTestCases", "PMD.AvoidFieldNameMatchingMethodName", "PMD.CloseResource",
            "PMD.LooseCoupling" })
    private static class TestServer {
        private final HttpServer server;
        private final ExecutorService executor;
        private volatile String capturedRequestBody;
        private volatile String capturedPath;
        private volatile Headers capturedHeaders;

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

        String baseUrl(String apiVersion) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/" + apiVersion;
        }

        String requestBody() {
            return capturedRequestBody;
        }

        String requestHeader(String name) {
            if (capturedHeaders == null) {
                return null;
            }
            return capturedHeaders.getFirst(name);
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
            capturedHeaders = exchange.getRequestHeaders();
            capturedRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }
    }
}
