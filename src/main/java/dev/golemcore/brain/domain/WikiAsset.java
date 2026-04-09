package dev.golemcore.brain.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiAsset {
    String name;
    String path;
    long size;
    String contentType;
}
