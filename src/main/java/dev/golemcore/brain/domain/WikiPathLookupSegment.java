package dev.golemcore.brain.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiPathLookupSegment {
    String slug;
    String path;
    boolean exists;
}
