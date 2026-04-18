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

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WikiControllerGraphTest {

    private static Path storageRoot;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        storageRoot = tempDir.resolve("wiki-controller-graph-test");
        registry.add("brain.storage-root", () -> storageRoot.toString());
        registry.add("brain.seed-demo-content", () -> "false");
        registry.add("brain.auth-disabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReportOrphansAndDanglingLinksAcrossTheWiki() throws Exception {
        createPage("hub", "Hub page links to [Connected](connected) and to [Ghost](does-not-exist).");
        createPage("connected", "Backlink target.");
        createPage("orphan", "No one links to this page.");
        createPage("also-orphan", "Nor this one.");

        mockMvc.perform(get("/api/spaces/default/wiki/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orphans[*].path", hasItem("orphan")))
                .andExpect(jsonPath("$.orphans[*].path", hasItem("also-orphan")))
                .andExpect(jsonPath("$.orphans[*].path", hasItem("hub")))
                .andExpect(jsonPath("$.dangling[*].toPath", hasItem("does-not-exist")))
                .andExpect(jsonPath("$.dangling[*].fromPath", hasItem("hub")));
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
