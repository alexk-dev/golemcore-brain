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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * LRU-bounded in-memory rate limiter for login attempts. Two counters are
 * maintained:
 * <ul>
 * <li><b>(IP, username)</b>: 5 failures / 15min — fast lockout against
 * credential stuffing for a specific account.</li>
 * <li><b>IP-only</b>: 20 failures / 15min — protects honest users against an
 * attacker who sprays distinct usernames from one IP to evict their entries
 * from the (IP, username) LRU.</li>
 * </ul>
 *
 * <p>
 * A successful login clears the (IP, username) entry only — the IP-only counter
 * survives so that a successful guess midway through a spray does not erase the
 * per-IP attempt history.
 * </p>
 *
 * <p>
 * This is a single-process limiter — do not rely on it as the only defence in a
 * multi-instance deployment, but for a single replica it cuts off
 * password-spray and credential-stuffing.
 * </p>
 */
public class LoginRateLimiter {

    public static final int MAX_FAILURES_PER_USER = 5;
    public static final int MAX_FAILURES_PER_IP = 20;
    public static final Duration WINDOW = Duration.ofMinutes(15);

    private static final int MAX_USER_ENTRIES = 10_000;
    private static final int MAX_IP_ENTRIES = 1_000;

    private final Clock clock;
    private final Map<String, Attempt> userAttempts;
    private final Map<String, Attempt> ipAttempts;

    public LoginRateLimiter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.userAttempts = boundedLru(MAX_USER_ENTRIES);
        this.ipAttempts = boundedLru(MAX_IP_ENTRIES);
    }

    public synchronized void requireNotBlocked(String ip, String username) {
        Instant now = clock.instant();
        Duration retryUser = checkBlocked(userAttempts, userKey(ip, username), MAX_FAILURES_PER_USER, now);
        Duration retryIp = checkBlocked(ipAttempts, ipKey(ip), MAX_FAILURES_PER_IP, now);
        Duration retryAfter = longerOf(retryUser, retryIp);
        if (retryAfter != null) {
            throw new LoginThrottledException(retryAfter);
        }
    }

    public synchronized void recordFailure(String ip, String username) {
        Instant now = clock.instant();
        bump(userAttempts, userKey(ip, username), now);
        bump(ipAttempts, ipKey(ip), now);
    }

    /**
     * Clears the (IP, username) entry. The IP-only counter is intentionally
     * retained — if the caller succeeded, that single account is no longer being
     * attacked, but the same IP may still be spraying other usernames, and we want
     * that to remain visible.
     */
    public synchronized void recordSuccess(String ip, String username) {
        userAttempts.remove(userKey(ip, username));
    }

    private Duration checkBlocked(Map<String, Attempt> map, String key, int maxFailures, Instant now) {
        Attempt attempt = map.get(key);
        if (attempt == null) {
            return null;
        }
        if (now.isAfter(attempt.firstFailureAt.plus(WINDOW))) {
            map.remove(key);
            return null;
        }
        if (attempt.count >= maxFailures) {
            Duration retryAfter = Duration.between(now, attempt.firstFailureAt.plus(WINDOW));
            return retryAfter.isNegative() ? Duration.ZERO : retryAfter;
        }
        return null;
    }

    private void bump(Map<String, Attempt> map, String key, Instant now) {
        Attempt existing = map.get(key);
        if (existing == null || now.isAfter(existing.firstFailureAt.plus(WINDOW))) {
            map.put(key, new Attempt(1, now));
            return;
        }
        map.put(key, new Attempt(existing.count + 1, existing.firstFailureAt));
    }

    private static Duration longerOf(Duration a, Duration b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.compareTo(b) >= 0 ? a : b;
    }

    private static String userKey(String ip, String username) {
        String safeIp = ip == null ? "?" : ip;
        String safeUser = username == null ? "?" : username.trim().toLowerCase(Locale.ROOT);
        return safeIp + "|" + safeUser;
    }

    private static String ipKey(String ip) {
        return ip == null ? "?" : ip;
    }

    private static <K, V> Map<K, V> boundedLru(int maxEntries) {
        return new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxEntries;
            }
        };
    }

    private record Attempt(int count, Instant firstFailureAt) {
    }
}
