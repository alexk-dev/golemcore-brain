package dev.golemcore.brain.adapter.in.web.dto;

import dev.golemcore.brain.domain.WikiImportPolicy;
import lombok.Data;

@Data
public class MarkdownImportSelectionPayload {
    private String sourcePath;
    private boolean selected = true;
    private WikiImportPolicy policy = WikiImportPolicy.OVERWRITE;
}
