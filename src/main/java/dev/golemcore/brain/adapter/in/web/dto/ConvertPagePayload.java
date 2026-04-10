package dev.golemcore.brain.adapter.in.web.dto;

import dev.golemcore.brain.domain.WikiNodeKind;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConvertPagePayload {

    @NotNull
    private WikiNodeKind targetKind;
}
