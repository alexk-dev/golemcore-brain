package dev.golemcore.brain.adapter.in.web;

import dev.golemcore.brain.config.WikiProperties;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WikiControllerImportTest {

    private static Path storageRoot;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        storageRoot = tempDir.resolve("wiki-controller-import-test");
        registry.add("brain.storage-root", () -> storageRoot.toString());
        registry.add("brain.seed-demo-content", () -> "false");
        registry.add("brain.auth-disabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WikiProperties wikiProperties;

    @Test
    void shouldPreviewAndApplyMarkdownZipImport() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siteTitle", is(wikiProperties.getSiteTitle())));

        MockMultipartFile archive = new MockMultipartFile(
                "file",
                "docs.zip",
                "application/zip",
                buildImportZip());
        MockMultipartFile previewOptions = new MockMultipartFile(
                "options",
                "",
                "application/json",
                """
                        {
                          "targetRootPath": "knowledge"
                        }
                        """.getBytes(StandardCharsets.UTF_8));
        MockMultipartFile applyOptions = new MockMultipartFile(
                "options",
                "",
                "application/json",
                """
                        {
                          "targetRootPath": "knowledge",
                          "items": [
                            {
                              "sourcePath": "guides/index.md",
                              "selected": true,
                              "policy": "OVERWRITE"
                            },
                            {
                              "sourcePath": "guides/setup.md",
                              "selected": true,
                              "policy": "KEEP_EXISTING"
                            }
                          ]
                        }
                        """.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/spaces/default/import/markdown/plan")
                        .file(archive)
                        .file(previewOptions))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.targetRootPath", is("knowledge")))
                .andExpect(jsonPath("$.items[0].path", is("knowledge/guides")))
                .andExpect(jsonPath("$.items[0].kind", is("SECTION")))
                .andExpect(jsonPath("$.items[1].path", is("knowledge/guides/setup")))
                .andExpect(jsonPath("$.items[1].kind", is("PAGE")));

        mockMvc.perform(multipart("/api/spaces/default/import/markdown/apply")
                        .file(archive)
                        .file(previewOptions))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount", is(2)))
                .andExpect(jsonPath("$.createdCount", is(2)))
                .andExpect(jsonPath("$.updatedCount", is(0)))
                .andExpect(jsonPath("$.importedRootPath", is("knowledge/guides")));

        mockMvc.perform(get("/api/spaces/default/pages/by-path").param("path", "knowledge/guides/setup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Setup")));

        mockMvc.perform(multipart("/api/spaces/default/import/markdown/apply")
                        .file(archive)
                        .file(applyOptions))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedCount", is(1)))
                .andExpect(jsonPath("$.createdCount", is(0)))
                .andExpect(jsonPath("$.updatedCount", is(1)))
                .andExpect(jsonPath("$.skippedCount", is(1)));
    }

    private byte[] buildImportZip() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8);
        zipOutputStream.putNextEntry(new ZipEntry("guides/index.md"));
        zipOutputStream.write("# Guides\n\nImported guides section".getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
        zipOutputStream.putNextEntry(new ZipEntry("guides/setup.md"));
        zipOutputStream.write("# Setup\n\nImported setup page".getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
        zipOutputStream.finish();
        zipOutputStream.close();
        return outputStream.toByteArray();
    }
}
