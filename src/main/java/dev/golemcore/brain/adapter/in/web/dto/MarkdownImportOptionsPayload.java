package dev.golemcore.brain.adapter.in.web.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class MarkdownImportOptionsPayload {
    private String targetRootPath = "";
    private List<MarkdownImportSelectionPayload> items = new ArrayList<>();
}
