package dev.golemcore.brain.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RenameAssetPayload {

    @NotBlank
    private String oldName;

    @NotBlank
    private String newName;
}
