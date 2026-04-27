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

import java.time.Duration;
import lombok.Getter;

/**
 * Thrown when {@link LoginRateLimiter} detects too many recent failed login
 * attempts for the caller. {@link #retryAfter} indicates how long the client
 * should wait before retrying.
 */
@Getter
public class LoginThrottledException extends RuntimeException {

    private final Duration retryAfter;

    public LoginThrottledException(Duration retryAfter) {
        super("Too many failed login attempts. Try again in "
                + Math.max(1L, retryAfter.toSeconds()) + " seconds.");
        this.retryAfter = retryAfter;
    }
}
