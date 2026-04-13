package dev.golemcore.brain.application.port.out;

import dev.golemcore.brain.domain.llm.ModelCatalogEntry;

public interface ModelRegistryDocumentPort {
    ModelCatalogEntry parseCatalogEntry(String json);
}
