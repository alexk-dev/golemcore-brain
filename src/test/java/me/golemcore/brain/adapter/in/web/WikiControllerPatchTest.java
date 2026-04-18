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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WikiControllerPatchTest {

    private static Path storageRoot;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        storageRoot = tempDir.resolve("wiki-controller-patch-test");
        registry.add("brain.storage-root", () -> storageRoot.toString());
        registry.add("brain.seed-demo-content", () -> "false");
        registry.add("brain.auth-disabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldAppendContentWhenRevisionMatches() throws Exception {
        String path = createPage("append", """
                # Runbook

                ## Status
                Operational
                """);
        String revision = currentRevision(path);

        mockMvc.perform(patch("/api/spaces/default/page")
                .param("path", path)
                .contentType("application/json")
                .content("""
                        {
                          "operation": "APPEND",
                          "content": "\\n## Appendix\\nExtra notes",
                          "expectedRevision": "%s"
                        }
                        """.formatted(revision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", containsString("Operational")))
                .andExpect(jsonPath("$.content", containsString("## Appendix")))
                .andExpect(jsonPath("$.revision", not(equalTo(revision))));
    }

    @Test
    void shouldPrependContentWhenRevisionMatches() throws Exception {
        String path = createPage("prepend", """
                Body content
                """);
        String revision = currentRevision(path);

        mockMvc.perform(patch("/api/spaces/default/page")
                .param("path", path)
                .contentType("application/json")
                .content("""
                        {
                          "operation": "PREPEND",
                          "content": "Preface line\\n\\n",
                          "expectedRevision": "%s"
                        }
                        """.formatted(revision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", containsString("Preface line")))
                .andExpect(jsonPath("$.content", containsString("Body content")));

        mockMvc.perform(get("/api/spaces/default/pages/by-path").param("path", path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", is("Preface line\n\nBody content")));
    }

    @Test
    void shouldReplaceSectionWithoutTouchingOtherSections() throws Exception {
        String path = createPage("replace", """
                # Runbook

                ## Status
                Old status text.

                ## Next Steps
                Review backlog.
                """);
        String revision = currentRevision(path);

        mockMvc.perform(patch("/api/spaces/default/page")
                .param("path", path)
                .contentType("application/json")
                .content("""
                        {
                          "operation": "REPLACE_SECTION",
                          "heading": "Status",
                          "content": "Updated status line.\\n",
                          "expectedRevision": "%s"
                        }
                        """.formatted(revision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", containsString("Updated status line.")))
                .andExpect(jsonPath("$.content", containsString("## Next Steps")))
                .andExpect(jsonPath("$.content", containsString("Review backlog.")))
                .andExpect(jsonPath("$.content", not(containsString("Old status text"))));
    }

    @Test
    void shouldRejectPatchWithStaleRevision() throws Exception {
        String path = createPage("stale", """
                Initial
                """);
        String staleRevision = currentRevision(path);

        mockMvc.perform(patch("/api/spaces/default/page")
                .param("path", path)
                .contentType("application/json")
                .content("""
                        {
                          "operation": "APPEND",
                          "content": "first writer wins\\n",
                          "expectedRevision": "%s"
                        }
                        """.formatted(staleRevision)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/spaces/default/page")
                .param("path", path)
                .contentType("application/json")
                .content("""
                        {
                          "operation": "APPEND",
                          "content": "stale writer loses\\n",
                          "expectedRevision": "%s"
                        }
                        """.formatted(staleRevision)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("PAGE_EDIT_CONFLICT")))
                .andExpect(jsonPath("$.expectedRevision", is(staleRevision)))
                .andExpect(jsonPath("$.currentPage.path", is(path)))
                .andExpect(jsonPath("$.currentPage.content", containsString("first writer wins")))
                .andExpect(jsonPath("$.currentPage.content", not(containsString("stale writer loses"))));
    }

    @Test
    void shouldReturn400WhenReplaceSectionHeadingNotFound() throws Exception {
        String path = createPage("missing-heading", """
                # Runbook

                ## Status
                Body
                """);
        String revision = currentRevision(path);

        mockMvc.perform(patch("/api/spaces/default/page")
                .param("path", path)
                .contentType("application/json")
                .content("""
                        {
                          "operation": "REPLACE_SECTION",
                          "heading": "Missing Heading",
                          "content": "irrelevant",
                          "expectedRevision": "%s"
                        }
                        """.formatted(revision)))
                .andExpect(status().isBadRequest());
    }

    private String createPage(String slug, String content) throws Exception {
        String escaped = content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");

        mockMvc.perform(post("/api/spaces/default/pages")
                .contentType("application/json")
                .content("""
                        {
                          "parentPath": "",
                          "title": "Doc-%s",
                          "slug": "%s",
                          "content": "%s",
                          "kind": "PAGE"
                        }
                        """.formatted(slug, slug, escaped)))
                .andExpect(status().isOk());
        return slug;
    }

    private String currentRevision(String path) throws Exception {
        MvcResult page = mockMvc.perform(get("/api/spaces/default/pages/by-path").param("path", path))
                .andExpect(status().isOk())
                .andReturn();
        return extractField(page.getResponse().getContentAsString(), "revision");
    }

    private String extractField(String body, String fieldName) {
        String marker = "\"" + fieldName + "\":\"";
        int startIndex = body.indexOf(marker);
        int valueStart = startIndex + marker.length();
        int valueEnd = body.indexOf('"', valueStart);
        return body.substring(valueStart, valueEnd);
    }
}
