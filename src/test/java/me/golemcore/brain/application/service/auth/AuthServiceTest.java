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

package me.golemcore.brain.application.service.auth;

import me.golemcore.brain.adapter.out.filesystem.auth.FileSessionRepository;
import me.golemcore.brain.adapter.out.filesystem.auth.FileUserRepository;
import me.golemcore.brain.config.WikiProperties;
import me.golemcore.brain.domain.auth.AuthConfigResponse;
import me.golemcore.brain.domain.auth.AuthResponse;
import me.golemcore.brain.domain.auth.UserRole;
import me.golemcore.brain.domain.auth.WikiUser;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldLoginResolveSessionAndEnforceRoles() {
        WikiProperties properties = new WikiProperties();
        properties.setStorageRoot(tempDir.resolve("wiki-auth"));
        properties.setAuthDisabled(false);
        properties.setPublicAccess(true);
        properties.setAdminUsername("admin");
        properties.setAdminEmail("admin@example.com");
        properties.setAdminPassword("admin");

        PasswordHasher passwordHasher = new PasswordHasher();
        FileUserRepository userRepository = new FileUserRepository(properties);
        FileSessionRepository sessionRepository = new FileSessionRepository(properties);
        AuthService authService = new AuthService(properties, userRepository, sessionRepository, passwordHasher);
        authService.initialize();
        sessionRepository.initialize();

        AuthResponse authResponse = authService.login("admin", "admin");
        assertEquals("admin", authResponse.getUser().getUsername());
        assertNotNull(authResponse.getMessage());

        AuthConfigResponse authConfig = authService.getConfig(Optional.of(authResponse.getMessage()));
        assertTrue(authConfig.getUser().getRole() == UserRole.ADMIN);

        authService.requireEditAccess(Optional.of(authResponse.getMessage()));
        authService.requireUserManagement(Optional.of(authResponse.getMessage()));

        WikiUser viewer = WikiUser.builder()
                .id("viewer-1")
                .username("viewer")
                .email("viewer@example.com")
                .passwordHash(passwordHasher.hash("viewer"))
                .role(UserRole.VIEWER)
                .build();
        userRepository.save(viewer);
        AuthResponse viewerResponse = authService.login("viewer", "viewer");
        assertThrows(AuthAccessDeniedException.class,
                () -> authService.requireEditAccess(Optional.of(viewerResponse.getMessage())));

        AuthConfigResponse anonymousConfig = authService.getConfig(Optional.empty());
        assertTrue(anonymousConfig.isPublicAccess());
        assertFalse(anonymousConfig.isAuthDisabled());

        assertThrows(AuthUnauthorizedException.class, () -> authService.login("admin", "wrong"));
    }
}
