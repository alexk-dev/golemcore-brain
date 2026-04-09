package dev.golemcore.brain.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiConfigResponse {
    String siteTitle;
    String rootPath;
}
