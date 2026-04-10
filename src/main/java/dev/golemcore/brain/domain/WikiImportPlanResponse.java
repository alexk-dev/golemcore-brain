package dev.golemcore.brain.domain;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiImportPlanResponse {
    List<WikiImportItem> items;
}
