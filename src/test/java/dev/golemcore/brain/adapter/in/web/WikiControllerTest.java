package dev.golemcore.brain.adapter.in.web;

import dev.golemcore.brain.config.WikiProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WikiControllerTest {

    private static Path storageRoot;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        storageRoot = tempDir.resolve("wiki-controller-test");
        registry.add("brain.storage-root", () -> storageRoot.toString());
        registry.add("brain.seed-demo-content", () -> "false");
        registry.add("brain.auth-disabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WikiProperties wikiProperties;

    @Test
    void shouldExposeTreePageLookupAndCrudFlows() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siteTitle", is(wikiProperties.getSiteTitle())))
                .andExpect(jsonPath("$.authDisabled", is(true)));

        mockMvc.perform(get("/api/spaces/default/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind", is("ROOT")));

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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path", is("operations")));

        mockMvc.perform(post("/api/spaces/default/pages")
                .contentType("application/json")
                .content("""
                        {
                          "parentPath": "operations",
                          "title": "Runbook",
                          "slug": "runbook",
                          "content": "[Checklist](../shared/checklist)",
                          "kind": "PAGE"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path", is("operations/runbook")));

        mockMvc.perform(post("/api/spaces/default/pages")
                .contentType("application/json")
                .content("""
                        {
                          "parentPath": "",
                          "title": "Shared",
                          "slug": "shared",
                          "content": "Shared section",
                          "kind": "SECTION"
                        }
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/spaces/default/pages")
                .contentType("application/json")
                .content("""
                        {
                          "parentPath": "shared",
                          "title": "Checklist",
                          "slug": "checklist",
                          "content": "Checklist body",
                          "kind": "PAGE"
                        }
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/spaces/default/pages/by-path").param("path", "operations/runbook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Runbook")));

        mockMvc.perform(get("/api/spaces/default/pages/lookup").param("path", "operations/runbook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists", is(true)))
                .andExpect(jsonPath("$.segments", hasSize(2)));

        mockMvc.perform(put("/api/spaces/default/page")
                .param("path", "operations/runbook")
                .contentType("application/json")
                .content(
                        """
                                {
                                  "title": "Release Runbook",
                                  "slug": "release-runbook",
                                  "content": "Updated body with [Checklist](../shared/checklist) and [Missing](../shared/missing)"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path", is("operations/release-runbook")));

        mockMvc.perform(post("/api/spaces/default/pages/ensure")
                .contentType("application/json")
                .content("""
                        {
                          "path": "operations/generated-page",
                          "targetTitle": "Generated Page"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path", is("operations/generated-page")));

        mockMvc.perform(get("/api/spaces/default/search/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode", is("lucene+sqlite-embeddings")))
                .andExpect(jsonPath("$.ready", is(true)))
                .andExpect(jsonPath("$.indexedDocuments", greaterThan(0)))
                .andExpect(jsonPath("$.lastUpdatedAt").exists());

        mockMvc.perform(get("/api/spaces/default/search").param("q", "updated"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].path", is("operations/release-runbook")));

        mockMvc.perform(get("/api/spaces/default/links").param("path", "operations/release-runbook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outgoings", hasSize(1)))
                .andExpect(jsonPath("$.brokenOutgoings", hasSize(1)));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "hello asset".getBytes());

        mockMvc.perform(multipart("/api/spaces/default/pages/assets")
                .file(file)
                .param("path", "operations/release-runbook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("notes.txt")))
                .andExpect(jsonPath("$.path", is(
                        "/api/spaces/default/assets?path=operations/release-runbook&name=notes.txt")));

        mockMvc.perform(get("/api/spaces/default/pages/assets").param("path", "operations/release-runbook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/spaces/default/assets")
                .param("path", "operations/release-runbook")
                .param("name", "notes.txt"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/spaces/default/pages")
                .contentType("application/json")
                .content("""
                        {
                          "parentPath": "",
                          "title": "Empty Section",
                          "slug": "empty-section",
                          "content": "Empty",
                          "kind": "SECTION"
                        }
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/spaces/default/page/convert")
                .param("path", "empty-section")
                .contentType("application/json")
                .content("""
                        {
                          "targetKind": "PAGE"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind", is("PAGE")));

        mockMvc.perform(delete("/api/spaces/default/page").param("path", "operations/generated-page"))
                .andExpect(status().isOk());
    }
}
