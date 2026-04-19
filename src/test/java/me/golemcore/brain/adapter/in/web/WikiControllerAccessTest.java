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

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WikiControllerAccessTest {

    private static Path storageRoot;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        storageRoot = tempDir.resolve("wiki-controller-access-test");
        registry.add("brain.storage-root", () -> storageRoot.toString());
        registry.add("brain.seed-demo-content", () -> "false");
        registry.add("brain.auth-disabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldTrackAccessCountAcrossReads() throws Exception {
        createPage("warm", "Body");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/spaces/default/pages/by-path").param("path", "warm"))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/spaces/default/pages/by-path").param("path", "warm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessCount", is(4)))
                .andExpect(jsonPath("$.lastAccessedAt").isString());
    }

    @Test
    void shouldReturnTopPagesByAccessCount() throws Exception {
        createPage("hot", "Body");
        createPage("cold", "Body");

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/spaces/default/pages/by-path").param("path", "hot"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/spaces/default/pages/by-path").param("path", "cold"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/spaces/default/wiki/access/top").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].path", is("hot")))
                .andExpect(jsonPath("$.items[0].accessCount", is(5)));
    }

    private void createPage(String slug, String content) throws Exception {
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        mockMvc.perform(post("/api/spaces/default/pages")
                .contentType("application/json")
                .content("""
                        {
                          "parentPath": "",
                          "title": "%s",
                          "slug": "%s",
                          "content": "%s",
                          "kind": "PAGE"
                        }
                        """.formatted(slug, slug, escaped)))
                .andExpect(status().isOk());
    }
}
