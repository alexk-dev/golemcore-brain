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

package me.golemcore.brain.adapter.out.http;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutboundUrlGuardTest {

    private final OutboundUrlGuard guard = new OutboundUrlGuard(false);

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/x", // IPv4 loopback
            "http://10.0.0.1/x", // private (RFC 1918)
            "http://172.16.0.1/x", // private (RFC 1918)
            "http://192.168.1.1/x", // private (RFC 1918)
            "http://169.254.169.254/latest/meta", // AWS IMDS link-local
            "http://0.0.0.0/x", // any-local
            "http://0.1.2.3/x", // 0.0.0.0/8
            "http://255.255.255.255/x", // broadcast
            "http://100.64.0.1/x", // CGNAT
            "http://[::1]/x", // IPv6 loopback
            "http://[fc00::1]/x", // IPv6 ULA
            "http://[fd00::1]/x", // IPv6 ULA
            "http://[fe80::1]/x", // IPv6 link-local
            "http://[::ffff:127.0.0.1]/x", // IPv4-mapped loopback (Java often returns Inet4Address)
            "http://[::ffff:10.0.0.1]/x", // IPv4-mapped private
    })
    void shouldRejectPrivateOrLoopbackTargets(String url) {
        assertThrows(IllegalArgumentException.class, () -> guard.requirePublicHttp(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://example.com/",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "gopher://example.com/",
            "data:text/plain,hi",
    })
    void shouldRejectNonHttpSchemes(String url) {
        assertThrows(IllegalArgumentException.class, () -> guard.requirePublicHttp(url));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "https://" })
    void shouldRejectEmptyOrHostlessUrls(String url) {
        assertThrows(IllegalArgumentException.class, () -> guard.requirePublicHttp(url));
    }

    @ParameterizedTest
    @ValueSource(strings = { "http://1.1.1.1/x", "https://8.8.8.8/", "https://example.com/x" })
    void shouldAllowPublicTargets(String url) {
        // example.com lookups need the network in CI; if DNS fails the test will throw
        // "Unable to
        // resolve host". Filter such transient failures by catching
        // IllegalArgumentException whose
        // message starts with "Unable to resolve host".
        try {
            guard.requirePublicHttp(url);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() == null || !e.getMessage().startsWith("Unable to resolve host")) {
                throw e;
            }
            // DNS not available in the sandbox — accept the test as not applicable.
        }
    }

    @org.junit.jupiter.api.Test
    void shouldBypassChecksWhenAllowPrivateAddressesIsTrue() {
        OutboundUrlGuard permissive = new OutboundUrlGuard(true);
        assertDoesNotThrow(() -> permissive.requirePublicHttp("http://127.0.0.1/x"));
        assertDoesNotThrow(() -> permissive.requirePublicHttp("http://10.0.0.1/x"));
    }
}
