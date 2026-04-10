package dev.golemcore.brain.domain;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiImportApplyResponse {
    int importedCount;
    int createdCount;
    int updatedCount;
    int skippedCount;
    List<WikiImportItem> items;
}
