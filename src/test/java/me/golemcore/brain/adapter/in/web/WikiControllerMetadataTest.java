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
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WikiControllerMetadataTest {

    private static Path storageRoot;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        storageRoot = tempDir.resolve("wiki-controller-metadata-test");
        registry.add("brain.storage-root", () -> storageRoot.toString());
        registry.add("brain.seed-demo-content", () -> "false");
        registry.add("brain.auth-disabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldPersistAndReturnTagsAndSummary() throws Exception {
        mockMvc.perform(post("/api/spaces/default/pages")
                .contentType("application/json")
                .content("""
                        {
                          "parentPath": "",
                          "title": "Tagged",
                          "slug": "tagged",
                          "content": "Body",
                          "kind": "PAGE",
                          "tags": ["onboarding", "team"],
                          "summary": "Team onboarding overview"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", contains("onboarding", "team")))
                .andExpect(jsonPath("$.summary", is("Team onboarding overview")));

        mockMvc.perform(get("/api/spaces/default/pages/by-path").param("path", "tagged"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", contains("onboarding", "team")))
                .andExpect(jsonPath("$.summary", is("Team onboarding overview")))
                .andExpect(jsonPath("$.content", is("Body")));
    }

    @Test
    void shouldUpdateTagsThroughPutEndpoint() throws Exception {
        mockMvc.perform(post("/api/spaces/default/pages")
                .contentType("application/json")
                .content("""
                        {
                          "parentPath": "",
                          "title": "Tags Update",
                          "slug": "tags-update",
                          "content": "First",
                          "kind": "PAGE"
                        }
                        """))
                .andExpect(status().isOk());
        MvcResult snapshot = mockMvc.perform(get("/api/spaces/default/pages/by-path").param("path", "tags-update"))
                .andExpect(status().isOk())
                .andReturn();
        String revision = extractField(snapshot.getResponse().getContentAsString(), "revision");

        mockMvc.perform(put("/api/spaces/default/page")
                .param("path", "tags-update")
                .contentType("application/json")
                .content("""
                        {
                          "title": "Tags Update",
                          "slug": "tags-update",
                          "content": "First",
                          "tags": ["alpha", "beta"],
                          "summary": "Revised",
                          "revision": "%s"
                        }
                        """.formatted(revision)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/spaces/default/pages/by-path").param("path", "tags-update"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tags", contains("alpha", "beta")))
                .andExpect(jsonPath("$.summary", is("Revised")))
                .andExpect(jsonPath("$.content", is("First")));
    }

    private String extractField(String body, String fieldName) {
        String marker = "\"" + fieldName + "\":\"";
        int s = body.indexOf(marker) + marker.length();
        int e = body.indexOf('"', s);
        return body.substring(s, e);
    }
}
