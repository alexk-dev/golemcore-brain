package dev.golemcore.brain.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdatePagePayload {

    @NotBlank
    private String title;

    private String slug;

    private String content = "";
}
