package dev.golemcore.brain.web.dto;

import dev.golemcore.brain.domain.WikiNodeKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreatePagePayload {

    private String parentPath = "";

    @NotBlank
    private String title;

    private String slug;

    private String content = "";

    @NotNull
    private WikiNodeKind kind;
}
