package dev.golemcore.brain.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdatePagePayload {

    @NotBlank
    private String title;

    private String slug;

    private String content = "";
}
