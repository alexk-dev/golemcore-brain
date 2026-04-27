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

package me.golemcore.brain.web;

import jakarta.servlet.http.Cookie;
import java.nio.file.Path;
import me.golemcore.brain.web.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the production security posture: Spring Security headers are emitted
 * on every response, mutating session-cookie requests without an X-XSRF-TOKEN
 * are blocked with 403, and Bearer-token requests bypass CSRF (the JWT filter
 * authenticates them out-of-band).
 *
 * <p>
 * This test deliberately re-enables CSRF (the project-wide
 * {@code src/test/resources/application.properties} disables it for the rest of
 * the suite) so we exercise the prod-equivalent filter chain. Because the
 * global override sets {@code brain.security.csrf-enabled=false}, we override
 * it again here and rebuild the MockMvc with
 * {@link org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers#springSecurity()}.
 * </p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "brain.security.csrf-enabled=true",
        "brain.security.cookie-secure=false"
})
class SecurityHeadersAndCsrfIntegrationTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("brain.storage-root", () -> tempDir.resolve("security-headers-test").toString());
        registry.add("brain.seed-demo-content", () -> "false");
        registry.add("brain.auth-disabled", () -> "false");
        registry.add("brain.public-access", () -> "false");
        registry.add("brain.admin-username", () -> "admin");
        registry.add("brain.admin-email", () -> "admin@example.com");
        registry.add("brain.admin-password", () -> "admin");
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private MockMvc mockMvc;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        // Rebuild MockMvc with the full security filter chain so CSRF/headers behave as
        // in prod.
        // Add the JWT filter explicitly — apply(springSecurity()) only registers the
        // Spring
        // Security chain, and our JwtAuthenticationFilter is a separate @Order(...)
        // servlet
        // filter that MockMvc would otherwise skip.
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(jwtAuthenticationFilter)
                .apply(springSecurity())
                .build();
    }

    @Test
    void shouldEmitSecurityHeadersOnEveryResponse() throws Exception {
        // Use secure(true) so Spring Security's HSTS writer emits
        // Strict-Transport-Security
        // (the default requestMatcher only adds it for HTTPS requests).
        mockMvc.perform(get("/api/auth/config").secure(true))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("Strict-Transport-Security"))
                .andExpect(header().exists("Referrer-Policy"))
                .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    void shouldRejectMutatingPostWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAcceptPostWithMatchingXsrfTokenCookieAndHeader() throws Exception {
        // Pull the CSRF cookie from any GET first, then echo it as a header on POST.
        MvcResult getResult = mockMvc.perform(get("/api/auth/config")).andReturn();
        Cookie xsrf = getResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(xsrf, "XSRF-TOKEN cookie must be issued on idempotent GET");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .cookie(xsrf)
                .header("X-XSRF-TOKEN", xsrf.getValue())
                .content("{\"identifier\":\"admin\",\"password\":\"admin\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldBypassCsrfForBearerAuthenticatedRequests() throws Exception {
        // A garbage Bearer token must hit JwtAuthenticationFilter and produce 401 —
        // proving the
        // CSRF check was bypassed for this request class. Anything else
        // (200/403/405/500) means
        // the bypass is broken.
        int status = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer not-a-real-token")
                .content("{\"identifier\":\"admin\",\"password\":\"admin\"}"))
                .andReturn()
                .getResponse()
                .getStatus();
        assertEquals(401, status,
                "Bearer POST with invalid token must be rejected by JWT filter as 401, not by CSRF");
    }
}
