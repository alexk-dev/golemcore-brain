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

import me.golemcore.brain.testsupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginRateLimiterTest {

    private MutableClock clock;
    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        limiter = new LoginRateLimiter(clock);
    }

    @Test
    void shouldAllowAttemptsBelowPerUserThreshold() {
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES_PER_USER - 1; i++) {
            limiter.recordFailure("1.1.1.1", "alice");
        }
        assertDoesNotThrow(() -> limiter.requireNotBlocked("1.1.1.1", "alice"));
    }

    @Test
    void shouldBlockSpecificUserAfterMaxFailures() {
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES_PER_USER; i++) {
            limiter.recordFailure("1.1.1.1", "alice");
        }
        assertThrows(LoginThrottledException.class,
                () -> limiter.requireNotBlocked("1.1.1.1", "alice"));
    }

    @Test
    void shouldNotBlockOtherUserOnSameIpUntilPerIpCapHit() {
        // Five fails on "alice" lock alice but not bob (per-IP cap is 20).
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES_PER_USER; i++) {
            limiter.recordFailure("1.1.1.1", "alice");
        }
        assertDoesNotThrow(() -> limiter.requireNotBlocked("1.1.1.1", "bob"));
    }

    @Test
    void shouldBlockEntireIpAfterPerIpCapEvenForFreshUsername() {
        // Spray 20 distinct usernames from one IP — should trip the per-IP guard
        // and protect any honest user (e.g. "victim") on that IP from further
        // attempts coming from that source.
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES_PER_IP; i++) {
            limiter.recordFailure("1.1.1.1", "user-" + i);
        }
        assertThrows(LoginThrottledException.class,
                () -> limiter.requireNotBlocked("1.1.1.1", "victim"));
    }

    @Test
    void shouldUnblockAfterWindowElapses() {
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES_PER_USER; i++) {
            limiter.recordFailure("1.1.1.1", "alice");
        }
        clock.advance(LoginRateLimiter.WINDOW.plusSeconds(1));
        assertDoesNotThrow(() -> limiter.requireNotBlocked("1.1.1.1", "alice"));
    }

    @Test
    void shouldClearPerUserOnSuccessButRetainPerIp() {
        // Spray 20 failed usernames to trip the per-IP counter.
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES_PER_IP; i++) {
            limiter.recordFailure("1.1.1.1", "user-" + i);
        }
        // A success on user-0 must not unblock the per-IP counter.
        limiter.recordSuccess("1.1.1.1", "user-0");
        assertThrows(LoginThrottledException.class,
                () -> limiter.requireNotBlocked("1.1.1.1", "victim"));
    }

    @Test
    void shouldExposeRetryAfterDuration() {
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES_PER_USER; i++) {
            limiter.recordFailure("1.1.1.1", "alice");
        }
        clock.advance(Duration.ofMinutes(1));
        LoginThrottledException thrown = assertThrows(LoginThrottledException.class,
                () -> limiter.requireNotBlocked("1.1.1.1", "alice"));
        // First failure was at t=0; window ends at +15min; we're at +1min, so
        // retryAfter is 14min.
        Duration expectedAtLeast = Duration.ofMinutes(13);
        Duration expectedAtMost = Duration.ofMinutes(15);
        Duration actual = thrown.getRetryAfter();
        org.junit.jupiter.api.Assertions.assertTrue(actual.compareTo(expectedAtLeast) >= 0
                && actual.compareTo(expectedAtMost) <= 0,
                () -> "retryAfter " + actual + " not in [" + expectedAtLeast + "," + expectedAtMost + "]");
    }

    @Test
    void shouldNotPersistFailuresAcrossWindowBoundary() {
        limiter.recordFailure("1.1.1.1", "alice");
        clock.advance(LoginRateLimiter.WINDOW.plusSeconds(1));
        // Previous failure has expired — counter starts fresh.
        for (int i = 0; i < LoginRateLimiter.MAX_FAILURES_PER_USER - 1; i++) {
            limiter.recordFailure("1.1.1.1", "alice");
        }
        assertDoesNotThrow(() -> limiter.requireNotBlocked("1.1.1.1", "alice"));
    }
}
