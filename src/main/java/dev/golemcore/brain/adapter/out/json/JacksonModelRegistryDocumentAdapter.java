package dev.golemcore.brain.adapter.out.json;

import dev.golemcore.brain.application.port.out.ModelRegistryDocumentPort;
import dev.golemcore.brain.domain.llm.ModelCatalogEntry;
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
