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

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WikiControllerTxTest {

    private static Path storageRoot;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        storageRoot = tempDir.resolve("wiki-controller-tx-test");
        registry.add("brain.storage-root", () -> storageRoot.toString());
        registry.add("brain.seed-demo-content", () -> "false");
        registry.add("brain.auth-disabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldApplyBatchOfCreateAndUpdateAtomically() throws Exception {
        createPage("existing", "Old body");
        String existingRevision = currentRevision("existing");

        mockMvc.perform(post("/api/spaces/default/wiki/tx")
                .contentType("application/json")
                .content("""
                        {
                          "operations": [
                            {
                              "op": "CREATE",
                              "parentPath": "",
                              "slug": "new-one",
                              "title": "New One",
                              "content": "fresh",
                              "kind": "PAGE"
                            },
                            {
                              "op": "UPDATE",
                              "path": "existing",
                              "title": "Existing",
                              "slug": "existing",
                              "content": "new body",
                              "expectedRevision": "%s"
                            }
                          ]
                        }
                        """.formatted(existingRevision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].path", is("new-one")))
                .andExpect(jsonPath("$.results[1].path", is("existing")));

        mockMvc.perform(get("/api/spaces/default/pages/by-path").param("path", "new-one"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/spaces/default/pages/by-path").param("path", "existing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", is("new body")));
    }

    @Test
    void shouldRejectEntireBatchWhenAnyRevisionIsStale() throws Exception {
        createPage("stale-target", "original");

        mockMvc.perform(post("/api/spaces/default/pages")
                .contentType("application/json")
                .content("""
                        {
                          "parentPath": "",
                          "title": "Stale Target",
                          "slug": "stale-target",
                          "content": "concurrent",
                          "kind": "PAGE"
                        }
                        """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/spaces/default/wiki/tx")
                .contentType("application/json")
                .content("""
                        {
                          "operations": [
                            {
                              "op": "CREATE",
                              "parentPath": "",
                              "slug": "should-not-exist",
                              "title": "Should Not Exist",
                              "content": "",
                              "kind": "PAGE"
                            },
                            {
                              "op": "UPDATE",
                              "path": "stale-target",
                              "title": "Stale",
                              "slug": "stale-target",
                              "content": "attempted",
                              "expectedRevision": "deadbeef-not-a-real-revision"
                            }
                          ]
                        }
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("PAGE_EDIT_CONFLICT")));

        // Batch must be atomic — the CREATE should NOT have been persisted
        mockMvc.perform(get("/api/spaces/default/pages/by-path").param("path", "should-not-exist"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/spaces/default/pages/by-path").param("path", "stale-target"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", is("original")));
    }

    @Test
    void shouldApplyDeleteWithinBatch() throws Exception {
        createPage("doomed", "bye");

        mockMvc.perform(post("/api/spaces/default/wiki/tx")
                .contentType("application/json")
                .content("""
                        {
                          "operations": [
                            { "op": "DELETE", "path": "doomed" }
                          ]
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].path", is("doomed")));

        mockMvc.perform(get("/api/spaces/default/pages/by-path").param("path", "doomed"))
                .andExpect(status().isNotFound());
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

    private String currentRevision(String path) throws Exception {
        MvcResult page = mockMvc.perform(get("/api/spaces/default/pages/by-path").param("path", path))
                .andExpect(status().isOk())
                .andReturn();
        String body = page.getResponse().getContentAsString();
        String marker = "\"revision\":\"";
        int s = body.indexOf(marker) + marker.length();
        int e = body.indexOf('"', s);
        return body.substring(s, e);
    }
}
