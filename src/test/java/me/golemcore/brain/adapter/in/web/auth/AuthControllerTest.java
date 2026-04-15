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

package me.golemcore.brain.adapter.in.web.auth;

import me.golemcore.brain.application.service.auth.AuthService;
import jakarta.servlet.http.Cookie;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    private static Path storageRoot;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        storageRoot = tempDir.resolve("auth-controller-test");
        registry.add("brain.storage-root", () -> storageRoot.toString());
        registry.add("brain.seed-demo-content", () -> "false");
        registry.add("brain.auth-disabled", () -> "false");
        registry.add("brain.public-access", () -> "false");
        registry.add("brain.admin-username", () -> "admin");
        registry.add("brain.admin-email", () -> "admin@example.com");
        registry.add("brain.admin-password", () -> "admin");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateUpdateAndDeleteUsersAsAdmin() throws Exception {
        Cookie adminSession = login("admin", "admin");

        MvcResult createResult = mockMvc.perform(post("/api/auth/users")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "editor",
                          "email": "editor@example.com",
                          "password": "editor-pass",
                          "role": "EDITOR"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("editor")))
                .andExpect(jsonPath("$.email", is("editor@example.com")))
                .andReturn();

        String userId = extractJsonValue(createResult, "id");

        mockMvc.perform(put("/api/auth/users/{userId}", userId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "editor-updated",
                          "email": "updated@example.com",
                          "password": "updated-pass",
                          "role": "VIEWER"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("editor-updated")))
                .andExpect(jsonPath("$.email", is("updated@example.com")))
                .andExpect(jsonPath("$.role", is("VIEWER")));

        login("updated@example.com", "updated-pass");

        mockMvc.perform(delete("/api/auth/users/{userId}", userId)
                .cookie(adminSession))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "identifier": "updated@example.com",
                          "password": "updated-pass"
                        }
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectSelfDestructiveAdminChanges() throws Exception {
        Cookie adminSession = login("admin", "admin");
        String adminUserId = getSingleUserId(adminSession);

        mockMvc.perform(put("/api/auth/users/{userId}", adminUserId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "admin",
                          "email": "admin@example.com",
                          "password": "",
                          "role": "EDITOR"
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("You cannot remove your own admin role")));

        mockMvc.perform(delete("/api/auth/users/{userId}", adminUserId)
                .cookie(adminSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("You cannot delete your own user")));
    }

    @Test
    void shouldChangeOwnPasswordAndInvalidateOldSession() throws Exception {
        Cookie adminSession = login("admin", "admin");

        mockMvc.perform(post("/api/auth/password")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "currentPassword": "admin",
                          "newPassword": "new-admin-pass"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", is("Password changed")));

        mockMvc.perform(get("/api/auth/users").cookie(adminSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "identifier": "admin",
                          "password": "admin"
                        }
                        """))
                .andExpect(status().isUnauthorized());

        login("admin", "new-admin-pass");
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

    private String getSingleUserId(Cookie sessionCookie) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/users").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andReturn();
        return extractJsonValue(result, "id");
    }

    private String extractJsonValue(MvcResult result, String fieldName) throws Exception {
        String body = result.getResponse().getContentAsString();
        String marker = "\"" + fieldName + "\":\"";
        int startIndex = body.indexOf(marker);
        if (startIndex < 0) {
            throw new IllegalStateException("Field not found: " + fieldName + " in " + body);
        }
        int valueStart = startIndex + marker.length();
        int valueEnd = body.indexOf('"', valueStart);
        return body.substring(valueStart, valueEnd);
    }
}
