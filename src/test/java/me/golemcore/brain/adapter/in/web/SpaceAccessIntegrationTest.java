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

import me.golemcore.brain.application.service.auth.AuthService;
import jakarta.servlet.http.Cookie;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class SpaceAccessIntegrationTest {

    private static Path storageRoot;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        storageRoot = tempDir.resolve("space-access-integration-test");
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

    SpaceAccessIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    void shouldKeepSpacesAndApiKeysIsolatedAcrossHttpBoundaries() throws Exception {
        mockMvc.perform(get("/api/spaces/default/tree"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("Authentication required")));

        Cookie adminSession = login("admin", "admin");
        createSpace(adminSession, "reindex", "Reindex Space");
        createSpace(adminSession, "engineering", "Engineering");
        createSpace(adminSession, "support", "Support");

        mockMvc.perform(get("/api/spaces/reindex/tree").cookie(adminSession))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/spaces/engineering/reindex").cookie(adminSession))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/spaces/reindex").cookie(adminSession))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(post("/api/admin/spaces/engineering/reindex").cookie(adminSession))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("queued")))
                .andExpect(jsonPath("$.spacesQueued", is(1)));
        mockMvc.perform(post("/api/admin/spaces/reindex").cookie(adminSession))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("queued")))
                .andExpect(jsonPath("$.spacesQueued", greaterThanOrEqualTo(3)));

        createPage(adminSession, "engineering", "", "Operations", "ops", "Engineering operations", "SECTION")
                .andExpect(status().isOk());
        createPage(adminSession, "engineering", "ops", "Runbook", "runbook", "Alpha token belongs to engineering",
                "PAGE")
                .andExpect(status().isOk());
        createPage(adminSession, "support", "", "Operations", "ops", "Support operations", "SECTION")
                .andExpect(status().isOk());
        createPage(adminSession, "support", "ops", "Runbook", "runbook", "Beta token belongs to support", "PAGE")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/spaces/engineering/pages/by-path")
                .cookie(adminSession)
                .param("path", "ops/runbook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", is("Alpha token belongs to engineering")));
        mockMvc.perform(get("/api/spaces/support/pages/by-path")
                .cookie(adminSession)
                .param("path", "ops/runbook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", is("Beta token belongs to support")));

        mockMvc.perform(post("/api/spaces/engineering/search")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "query": "Alpha",
                          "mode": "fts"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits", hasSize(1)))
                .andExpect(jsonPath("$.hits[0].path", is("ops/runbook")));
        mockMvc.perform(post("/api/spaces/engineering/search")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "query": "Beta",
                          "mode": "fts"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits", hasSize(0)));
        mockMvc.perform(post("/api/spaces/support/search")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "query": "Beta",
                          "mode": "fts"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits", hasSize(1)))
                .andExpect(jsonPath("$.hits[0].path", is("ops/runbook")));

        MvcResult issuedKeyResult = mockMvc.perform(post("/api/spaces/engineering/api-keys")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "engineering automation",
                          "roles": ["EDITOR"]
                        }
                        """))
                .andExpect(status().isCreated())
                .andReturn();
        String token = extractString(issuedKeyResult, "token");
        String keyId = extractApiKeyId(issuedKeyResult);

        mockMvc.perform(post("/api/admin/spaces/engineering/reindex").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("Global admin required to manage spaces")));

        mockMvc.perform(get("/api/spaces").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].slug", is("engineering")));

        createPageWithToken(token, "engineering", "", "API Key Page", "api-key-page", "Created by scoped key", "PAGE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path", is("api-key-page")));
        createPageWithToken(token, "support", "", "Forbidden Page", "forbidden-page", "Should not be created",
                "PAGE")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("Access to space denied")));

        mockMvc.perform(delete("/api/api-keys/{keyId}", keyId).cookie(adminSession))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/spaces/engineering/tree").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error", is("API key revoked or expired")));
    }

    private ResultActions createPage(
            Cookie sessionCookie,
            String spaceSlug,
            String parentPath,
            String title,
            String slug,
            String content,
            String kind) throws Exception {
        return mockMvc.perform(post("/api/spaces/{spaceSlug}/pages", spaceSlug)
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(pagePayload(parentPath, title, slug, content, kind)));
    }

    private ResultActions createPageWithToken(
            String token,
            String spaceSlug,
            String parentPath,
            String title,
            String slug,
            String content,
            String kind) throws Exception {
        return mockMvc.perform(post("/api/spaces/{spaceSlug}/pages", spaceSlug)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(pagePayload(parentPath, title, slug, content, kind)));
    }

    private String pagePayload(String parentPath, String title, String slug, String content, String kind) {
        return """
                {
                  "parentPath": "%s",
                  "title": "%s",
                  "slug": "%s",
                  "content": "%s",
                  "kind": "%s"
                }
                """.formatted(parentPath, title, slug, content, kind);
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
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug", is(slug)))
                .andExpect(jsonPath("$.name", is(name)));
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

    private String extractApiKeyId(MvcResult result) throws Exception {
        Map<String, Object> body = readObject(result);
        Map<String, Object> apiKey = objectMap(body.get("apiKey"));
        return String.valueOf(apiKey.get("id"));
    }

    private String extractString(MvcResult result, String fieldName) throws Exception {
        Map<String, Object> body = readObject(result);
        return String.valueOf(body.get(fieldName));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readObject(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        return (Map<String, Object>) value;
    }
}
