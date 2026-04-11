package dev.golemcore.brain.domain;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiImportPlanResponse {
    String targetRootPath;
    int createCount;
    int updateCount;
    int skipCount;
    List<String> warnings;
    List<WikiImportItem> items;
}
