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

public enum LlmApiType {
    OPENAI("openai"), ANTHROPIC("anthropic"), GEMINI("gemini");

    private final String value;

    LlmApiType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static LlmApiType fromJson(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String normalized = source.trim().toLowerCase(Locale.ROOT);
        for (LlmApiType type : values()) {
            if (type.value.equals(normalized) || type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported LLM API type: " + source);
    }
}
