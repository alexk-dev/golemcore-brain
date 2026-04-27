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

package me.golemcore.brain.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProdProfileSecurityGuardTest {

    private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef-real-prod-secret";

    @Test
    void shouldAcceptFullyConfiguredProperties() {
        WikiProperties properties = validProperties();
        assertDoesNotThrow(new ProdProfileSecurityGuard(properties)::verify);
    }

    @Test
    void shouldRejectAuthDisabled() {
        WikiProperties properties = validProperties();
        properties.setAuthDisabled(true);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                new ProdProfileSecurityGuard(properties)::verify);
        assertTrue(error.getMessage().contains("auth-disabled"));
    }

    @Test
    void shouldRejectShortSecret() {
        WikiProperties properties = validProperties();
        properties.getJwt().setSecret("too-short");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                new ProdProfileSecurityGuard(properties)::verify);
        assertTrue(error.getMessage().contains("32 characters"));
    }

    @Test
    void shouldRejectMissingSecret() {
        WikiProperties properties = validProperties();
        properties.getJwt().setSecret(null);
        assertThrows(IllegalStateException.class,
                new ProdProfileSecurityGuard(properties)::verify);
    }

    @Test
    void shouldRejectWellKnownPlaceholderSecret() {
        WikiProperties properties = validProperties();
        properties.getJwt().setSecret(ProdProfileSecurityGuard.PLACEHOLDER_JWT_SECRET);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                new ProdProfileSecurityGuard(properties)::verify);
        assertTrue(error.getMessage().contains("placeholder"));
    }

    @Test
    void shouldRejectMissingAdminUsername() {
        WikiProperties properties = validProperties();
        properties.setAdminUsername(null);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                new ProdProfileSecurityGuard(properties)::verify);
        assertTrue(error.getMessage().contains("admin-username"));
    }

    @Test
    void shouldRejectBlankAdminPassword() {
        WikiProperties properties = validProperties();
        properties.setAdminPassword("   ");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                new ProdProfileSecurityGuard(properties)::verify);
        assertTrue(error.getMessage().contains("admin-password"));
    }

    private static WikiProperties validProperties() {
        WikiProperties properties = new WikiProperties();
        properties.setAuthDisabled(false);
        properties.getJwt().setSecret(VALID_SECRET);
        properties.setAdminUsername("admin");
        properties.setAdminPassword("strong-password");
        return properties;
    }
}
