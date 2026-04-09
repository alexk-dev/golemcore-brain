package dev.golemcore.brain.domain;

import java.io.InputStream;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiAssetContent {
    String name;
    String contentType;
    InputStream inputStream;
    long size;
}
