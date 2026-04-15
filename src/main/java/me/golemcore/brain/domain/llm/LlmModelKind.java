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

package me.golemcore.brain.domain.llm;

import java.util.Locale;

public enum LlmModelKind {
    CHAT("chat"), EMBEDDING("embedding");

    private final String value;

    LlmModelKind(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static LlmModelKind fromJson(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String normalized = source.trim().toLowerCase(Locale.ROOT);
        for (LlmModelKind kind : values()) {
            if (kind.value.equals(normalized) || kind.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unsupported LLM model kind: " + source);
    }
}
