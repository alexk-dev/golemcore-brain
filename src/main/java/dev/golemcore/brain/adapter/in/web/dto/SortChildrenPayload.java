package dev.golemcore.brain.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class SortChildrenPayload {

    @NotNull
    private List<String> orderedSlugs;
}
