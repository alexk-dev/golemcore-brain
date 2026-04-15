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

package me.golemcore.brain.adapter.out.json;

import me.golemcore.brain.application.port.out.ModelRegistryDocumentPort;
import me.golemcore.brain.domain.llm.ModelCatalogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class JacksonModelRegistryDocumentAdapter implements ModelRegistryDocumentPort {

    private final ObjectMapper objectMapper;

    @Override
    public ModelCatalogEntry parseCatalogEntry(String json) {
        try {
            return objectMapper.readValue(json, ModelCatalogEntry.class);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to parse model registry config", exception);
        }
    }
}
