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

import me.golemcore.brain.application.port.out.auth.PasswordEncoderPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;

/**
 * Password hashing with seamless migration from legacy unsalted SHA-256 hashes
 * to BCrypt. Stored hashes prefixed with {@code $2} are treated as BCrypt;
 * everything else is verified against the legacy SHA-256 hex format (64
 * lowercase hex chars). Callers should re-hash and persist whenever
 * {@link #needsRehash(String)} returns true after a successful match.
 */
@RequiredArgsConstructor
public class PasswordHasher {

    private static final String LEGACY_SHA256_REGEX = "^[0-9a-f]{64}$";

    private final PasswordEncoderPort passwordEncoder;

    public String hash(String password) {
        return passwordEncoder.encode(password);
    }

    public boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) {
            return false;
        }
        if (isBcrypt(storedHash)) {
            return passwordEncoder.matches(rawPassword, storedHash);
        }
        if (isLegacySha256(storedHash)) {
            return constantTimeEquals(legacySha256Hex(rawPassword), storedHash);
        }
        return false;
    }

    /**
     * Returns true when the stored hash is in the legacy SHA-256 format and should
     * be upgraded to BCrypt on the caller's next persistence write.
     */
    public boolean needsRehash(String storedHash) {
        return storedHash == null || !isBcrypt(storedHash);
    }

    private static boolean isBcrypt(String hash) {
        return hash.startsWith("$2");
    }

    private static boolean isLegacySha256(String hash) {
        return hash.matches(LEGACY_SHA256_REGEX);
    }

    private static String legacySha256Hex(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] result = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
