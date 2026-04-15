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

package me.golemcore.brain.application.space;

/**
 * Per-request holder for the active space id. Populated by SpaceResolverFilter
 * before any controller / service / repository code runs.
 */
public final class SpaceContextHolder {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private SpaceContextHolder() {
    }

    public static void set(String spaceId) {
        CURRENT.set(spaceId);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static String require() {
        String value = CURRENT.get();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Space context is not set for the current request");
        }
        return value;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
