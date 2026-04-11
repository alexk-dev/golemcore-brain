package dev.golemcore.brain.application.port.out;

import dev.golemcore.brain.domain.WikiIndexedDocument;
import java.util.List;

public interface WikiDocumentCatalogPort {
    List<WikiIndexedDocument> listDocuments(String spaceId);
}
