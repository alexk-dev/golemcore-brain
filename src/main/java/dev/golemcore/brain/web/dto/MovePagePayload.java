package dev.golemcore.brain.web.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MovePagePayload {

    @NotNull
    private String targetParentPath;

    private String targetSlug;

    private String beforeSlug;
}
