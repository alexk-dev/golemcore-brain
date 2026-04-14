package dev.golemcore.brain.domain;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiConfigResponse {
    boolean publicAccess;
    boolean hideLinkMetadataSection;
    boolean authDisabled;
    long maxAssetUploadSizeBytes;
    String siteTitle;
    String rootPath;
    String imageVersion;
}
