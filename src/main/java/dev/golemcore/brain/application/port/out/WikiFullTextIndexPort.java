package dev.golemcore.brain.application.port.out;

import dev.golemcore.brain.domain.WikiDocumentChangeSet;
import dev.golemcore.brain.domain.WikiSearchHit;
import java.util.List;
import java.util.Map;

public interface WikiFullTextIndexPort {
    void applyChanges(WikiDocumentChangeSet changeSet);

    List<WikiSearchHit> search(String spaceId, String query, int limit);

    List<String> listIndexedPaths(String spaceId);

    Map<String, String> listIndexedRevisions(String spaceId);

    int count(String spaceId);
}
