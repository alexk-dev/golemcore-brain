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

package me.golemcore.brain.adapter.out.http.llm;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import me.golemcore.brain.domain.Secret;
import me.golemcore.brain.domain.llm.LlmApiType;
import me.golemcore.brain.domain.llm.LlmProviderCheckResult;
import me.golemcore.brain.domain.llm.LlmProviderConfig;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpLlmProviderCheckAdapterTest {

    @Test
    void shouldUseReadableFallbackWhenNetworkErrorHasNoMessage() {
        IOException exception = new IOException((String) null);

        assertEquals("Provider check failed: could not reach the provider endpoint",
                HttpLlmProviderCheckAdapter.networkFailureMessage(exception));
    }

    @Test
    void shouldUseReadableCauseWhenNetworkErrorMessageIsEmpty() {
        IOException exception = new IOException("null");
        exception.initCause(new IllegalStateException("connection reset"));

        assertEquals("Provider check failed: connection reset",
                HttpLlmProviderCheckAdapter.networkFailureMessage(exception));
    }

    @Test
    void shouldKeepProviderNetworkErrorDetailsWhenPresent() {
        IOException exception = new IOException("Connection refused");

        assertEquals("Provider check failed: Connection refused",
                HttpLlmProviderCheckAdapter.networkFailureMessage(exception));
    }

    @Test
    void shouldDetectOpenAiModelsWithLangchain4jCatalog() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/models", this::respondWithOpenAiModels);
        server.start();
        try {
            HttpLlmProviderCheckAdapter adapter = new HttpLlmProviderCheckAdapter();

            LlmProviderCheckResult result = adapter.check("openai", LlmProviderConfig.builder()
                    .apiKey(Secret.of("sk-test"))
                    .apiType(LlmApiType.OPENAI)
                    .baseUrl("http://localhost:" + server.getAddress().getPort() + "/v1")
                    .requestTimeoutSeconds(5)
                    .build());

            assertTrue(result.success());
            assertEquals(200, result.statusCode());
            assertEquals(List.of("gpt-5.4", "text-embedding-3-large"), result.models());
            assertEquals("Provider responded to model listing (2 models)", result.message());
        } finally {
            server.stop(0);
        }
    }

    private void respondWithOpenAiModels(HttpExchange exchange) throws IOException {
        String response = """
                {
                  "object": "list",
                  "data": [
                    {"id": "gpt-5.4", "object": "model", "created": 0, "owned_by": "openai"},
                    {"id": "text-embedding-3-large", "object": "model", "created": 0, "owned_by": "openai"}
                  ]
                }
                """;
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
