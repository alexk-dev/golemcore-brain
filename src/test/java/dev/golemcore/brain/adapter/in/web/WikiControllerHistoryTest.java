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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WikiControllerHistoryTest {

    private static Path storageRoot;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        storageRoot = tempDir.resolve("wiki-controller-history-test");
        registry.add("brain.storage-root", () -> storageRoot.toString());
        registry.add("brain.seed-demo-content", () -> "false");
        registry.add("brain.auth-disabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldListAndRestorePageHistory() throws Exception {
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

        mockMvc.perform(put("/api/spaces/default/page")
                .param("path", "operations/runbook")
                .contentType("application/json")
                .content("""
                        {
                          "title": "Runbook v2",
                          "slug": "runbook",
                          "content": "Version two"
                        }
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/spaces/default/page")
                .param("path", "operations/runbook")
                .contentType("application/json")
                .content("""
                        {
                          "title": "Runbook v3",
                          "slug": "runbook",
                          "content": "Version three"
                        }
                        """))
                .andExpect(status().isOk());

        MvcResult historyResult = mockMvc
                .perform(get("/api/spaces/default/page/history").param("path", "operations/runbook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title", is("Runbook v2")))
                .andExpect(jsonPath("$[0].author", is("Local editor")))
                .andExpect(jsonPath("$[0].reason", is("Manual save")))
                .andExpect(jsonPath("$[1].title", is("Runbook v1")))
                .andReturn();

        String versionId = extractFirstVersionId(historyResult.getResponse().getContentAsString());

        mockMvc.perform(get("/api/spaces/default/page/history/version")
                .param("path", "operations/runbook")
                .param("versionId", versionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Runbook v2")))
                .andExpect(jsonPath("$.content", is("Version two")))
                .andExpect(jsonPath("$.author", is("Local editor")))
                .andExpect(jsonPath("$.reason", is("Manual save")));

        mockMvc.perform(post("/api/spaces/default/page/history/restore")
                .param("path", "operations/runbook")
                .param("versionId", versionId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/spaces/default/pages/by-path").param("path", "operations/runbook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Runbook v2")))
                .andExpect(jsonPath("$.content", is("Version two")));
    }

    private String extractFirstVersionId(String body) {
        String marker = "\"id\":\"";
        int startIndex = body.indexOf(marker);
        int valueStart = startIndex + marker.length();
        int valueEnd = body.indexOf('"', valueStart);
        return body.substring(valueStart, valueEnd);
    }
}
