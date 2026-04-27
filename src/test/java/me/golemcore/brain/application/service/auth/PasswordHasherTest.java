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

import me.golemcore.brain.adapter.out.security.BcryptPasswordEncoderAdapter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private PasswordHasher hasher;

    @BeforeEach
    void setUp() {
        // Cost-4 BCrypt keeps the test under a second; production uses cost 12.
        hasher = new PasswordHasher(new BcryptPasswordEncoderAdapter(new BCryptPasswordEncoder(4)));
    }

    @Test
    void shouldHashWithBcryptByDefault() {
        String hash = hasher.hash("hunter2");
        assertTrue(hash.startsWith("$2"), () -> "expected BCrypt prefix, got: " + hash);
        assertNotEquals("hunter2", hash);
    }

    @Test
    void shouldMatchOwnBcryptHash() {
        String hash = hasher.hash("hunter2");
        assertTrue(hasher.matches("hunter2", hash));
        assertFalse(hasher.matches("wrong", hash));
    }

    @Test
    void shouldVerifyLegacyUnsaltedSha256() {
        String legacyHash = legacySha256("hunter2");
        assertTrue(hasher.matches("hunter2", legacyHash));
        assertFalse(hasher.matches("wrong", legacyHash));
    }

    @Test
    void shouldNotAcceptUnknownHashFormats() {
        assertFalse(hasher.matches("hunter2", "plaintext-not-a-hash"));
        assertFalse(hasher.matches("hunter2", ""));
        assertFalse(hasher.matches("hunter2", null));
        assertFalse(hasher.matches(null, "anything"));
    }

    @Test
    void shouldFlagLegacyHashAsNeedingRehash() {
        assertTrue(hasher.needsRehash(legacySha256("hunter2")));
        assertTrue(hasher.needsRehash(null));
        assertTrue(hasher.needsRehash("not-a-bcrypt-hash"));
    }

    @Test
    void shouldNotFlagBcryptHashAsNeedingRehash() {
        String bcrypt = hasher.hash("hunter2");
        assertFalse(hasher.needsRehash(bcrypt));
    }

    private static String legacySha256(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] result = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
