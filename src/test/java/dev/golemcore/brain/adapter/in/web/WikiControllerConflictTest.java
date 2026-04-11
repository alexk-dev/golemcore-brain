package dev.golemcore.brain.adapter.in.web;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WikiControllerConflictTest {

    private static Path storageRoot;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        storageRoot = tempDir.resolve("wiki-controller-conflict-test");
        registry.add("brain.storage-root", () -> storageRoot.toString());
        registry.add("brain.seed-demo-content", () -> "false");
        registry.add("brain.auth-disabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRejectStaleSaveAndReturnCurrentPageSnapshot() throws Exception {
        mockMvc.perform(post("/api/spaces/default/pages")
                        .contentType("application/json")
                        .content("""
                                {
                                  "parentPath": "",
                                  "title": "Operations",
                                  "slug": "operations",
                                  "content": "Ops section",
                                  "kind": "SECTION"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/spaces/default/pages")
                        .contentType("application/json")
                        .content("""
                                {
                                  "parentPath": "operations",
                                  "title": "Runbook v1",
                                  "slug": "runbook",
                                  "content": "Version one",
                                  "kind": "PAGE"
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult originalPage = mockMvc.perform(get("/api/spaces/default/pages/by-path").param("path", "operations/runbook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").isString())
                .andReturn();

        String originalRevision = extractField(originalPage.getResponse().getContentAsString(), "revision");

        mockMvc.perform(put("/api/spaces/default/page")
                        .param("path", "operations/runbook")
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Runbook v2",
                                  "slug": "runbook",
                                  "content": "Version two",
                                  "revision": "%s"
                                }
                                """.formatted(originalRevision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Runbook v2")));

        mockMvc.perform(put("/api/spaces/default/page")
                        .param("path", "operations/runbook")
                        .contentType("application/json")
                        .content("""
                                {
                                  "title": "Runbook stale",
                                  "slug": "runbook",
                                  "content": "Stale overwrite",
                                  "revision": "%s"
                                }
                                """.formatted(originalRevision)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("PAGE_EDIT_CONFLICT")))
                .andExpect(jsonPath("$.expectedRevision", is(originalRevision)))
                .andExpect(jsonPath("$.currentPage.path", is("operations/runbook")))
                .andExpect(jsonPath("$.currentPage.title", is("Runbook v2")))
                .andExpect(jsonPath("$.currentPage.content", is("Version two")))
                .andExpect(jsonPath("$.currentPage.revision").isString())
                .andExpect(jsonPath("$.currentRevision").isString());

        mockMvc.perform(get("/api/spaces/default/pages/by-path").param("path", "operations/runbook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Runbook v2")))
                .andExpect(jsonPath("$.content", is("Version two")));
    }

    private String extractField(String body, String fieldName) {
        String marker = "\"" + fieldName + "\":\"";
        int startIndex = body.indexOf(marker);
        int valueStart = startIndex + marker.length();
        int valueEnd = body.indexOf('"', valueStart);
        return body.substring(valueStart, valueEnd);
    }
}
