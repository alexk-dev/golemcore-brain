package dev.golemcore.brain.application.service.auth;

import dev.golemcore.brain.adapter.out.filesystem.auth.FileSessionRepository;
import dev.golemcore.brain.adapter.out.filesystem.auth.FileUserRepository;
import dev.golemcore.brain.config.WikiProperties;
import dev.golemcore.brain.domain.auth.AuthConfigResponse;
import dev.golemcore.brain.domain.auth.AuthResponse;
import dev.golemcore.brain.domain.auth.UserRole;
import dev.golemcore.brain.domain.auth.WikiUser;
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
        assertThrows(AuthAccessDeniedException.class, () -> authService.requireEditAccess(Optional.of(viewerResponse.getMessage())));

        AuthConfigResponse anonymousConfig = authService.getConfig(Optional.empty());
        assertTrue(anonymousConfig.isPublicAccess());
        assertFalse(anonymousConfig.isAuthDisabled());

        assertThrows(AuthUnauthorizedException.class, () -> authService.login("admin", "wrong"));
    }
}
