package dev.golemcore.brain.adapter.in.web;

import dev.golemcore.brain.application.service.auth.AuthService;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PublicReadOnlyAccessIntegrationTest {

    private static Path storageRoot;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        storageRoot = tempDir.resolve("public-read-only-access-integration-test");
        registry.add("brain.storage-root", () -> storageRoot.toString());
        registry.add("brain.seed-demo-content", () -> "false");
        registry.add("brain.auth-disabled", () -> "false");
        registry.add("brain.public-access", () -> "true");
        registry.add("brain.admin-username", () -> "admin");
        registry.add("brain.admin-email", () -> "admin@example.com");
        registry.add("brain.admin-password", () -> "admin");
    }

    private final MockMvc mockMvc;

    PublicReadOnlyAccessIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void shouldAllowAnonymousReadsButRejectAnonymousWritesWhenPublicAccessIsEnabled() throws Exception {
        Cookie adminSession = login("admin", "admin");
        mockMvc.perform(post("/api/spaces/default/pages")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "parentPath": "",
                          "title": "Public Handbook",
                          "slug": "public-handbook",
                          "content": "Public alpha knowledge",
                          "kind": "PAGE"
                        }
                        """))
                .andExpect(status().isOk());

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "readme.txt",
                "text/plain",
                "public asset".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/spaces/default/pages/assets")
                .file(file)
                .cookie(adminSession)
                .param("path", "public-handbook"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicAccess", is(true)));
        mockMvc.perform(get("/api/spaces/default/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.children[0].path", is("public-handbook")));
        mockMvc.perform(get("/api/spaces/default/pages/by-path").param("path", "public-handbook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Public Handbook")))
                .andExpect(jsonPath("$.content", is("Public alpha knowledge")));
        mockMvc.perform(get("/api/spaces/default/search").param("q", "alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].path", is("public-handbook")));
        mockMvc.perform(get("/api/spaces/default/assets")
                .param("path", "public-handbook")
                .param("name", "readme.txt"))
                .andExpect(status().isOk())
                .andExpect(content().bytes("public asset".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(post("/api/spaces/default/pages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "parentPath": "",
                          "title": "Anonymous Edit",
                          "slug": "anonymous-edit",
                          "content": "Should be forbidden",
                          "kind": "PAGE"
                        }
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("Access to space denied")));
        mockMvc.perform(multipart("/api/spaces/default/pages/assets")
                .file(file)
                .param("path", "public-handbook"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error", is("Access to space denied")));
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
}
