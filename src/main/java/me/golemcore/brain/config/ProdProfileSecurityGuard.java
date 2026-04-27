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

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Refuses to start the application on the {@code prod} profile when
 * security-relevant settings are misconfigured. This is a defence-in-depth
 * measure: even though {@code application-prod.properties} hard-codes the
 * flags, an operator could still try to override them via JVM args,
 * environment, or an additional config import.
 */
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProdProfileSecurityGuard {

    private final WikiProperties wikiProperties;

    /**
     * Well-known placeholder shipped in the default profile. Any deployment whose
     * secret matches exactly is treated as misconfigured even though it satisfies
     * the length check.
     */
    static final String PLACEHOLDER_JWT_SECRET = "change-me-change-me-change-me-change-me-change-me";

    @PostConstruct
    public void verify() {
        if (wikiProperties.isAuthDisabled()) {
            throw new IllegalStateException(
                    "brain.auth-disabled=true is not permitted on the 'prod' profile");
        }
        String secret = wikiProperties.getJwt().getSecret();
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException(
                    "brain.jwt.secret must be set to a value of at least 32 characters on the 'prod' profile");
        }
        if (PLACEHOLDER_JWT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "brain.jwt.secret is set to the well-known placeholder value on the 'prod' profile");
        }
        if (isBlank(wikiProperties.getAdminUsername())) {
            throw new IllegalStateException(
                    "brain.admin-username (BRAIN_ADMIN_USERNAME) is required on the 'prod' profile");
        }
        if (isBlank(wikiProperties.getAdminPassword())) {
            throw new IllegalStateException(
                    "brain.admin-password (BRAIN_ADMIN_PASSWORD) is required on the 'prod' profile");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
