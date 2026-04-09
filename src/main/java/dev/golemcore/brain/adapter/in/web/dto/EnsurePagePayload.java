package dev.golemcore.brain.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EnsurePagePayload {

    @NotBlank
    private String path;

    private String targetTitle;
}
