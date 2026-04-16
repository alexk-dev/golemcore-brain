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

package me.golemcore.brain.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * Search strategy requested by API clients.
 *
 * <p>
 * {@code AUTO} currently follows the hybrid retrieval path, falling back to FTS
 * when embeddings are unavailable. {@code FTS} is deterministic full-text
 * search only. {@code HYBRID} combines embedding search and full-text search
 * when the embedding index is ready.
 */
public enum WikiSearchMode {
    AUTO, FTS, HYBRID;

    /**
     * Parses an API payload value into a supported search mode.
     *
     * <p>
     * Removed aliases such as {@code semantic} and {@code lexical} are rejected
     * deliberately so clients migrate to the explicit {@code hybrid} and
     * {@code fts} names.
     */
    public static WikiSearchMode from(String value) {
        String normalized = Optional.ofNullable(value)
                .orElse("auto")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-');
        if (normalized.isBlank() || "auto".equals(normalized)) {
            return AUTO;
        }
        if ("fts".equals(normalized)) {
            return FTS;
        }
        if ("hybrid".equals(normalized)) {
            return HYBRID;
        }
        throw new IllegalArgumentException("Unsupported search mode: " + value);
    }
}
