package dev.golemcore.brain.domain;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiPathLookupResult {
    String path;
    boolean exists;
    List<WikiPathLookupSegment> segments;
}
