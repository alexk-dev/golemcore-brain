package dev.golemcore.brain.domain.dynamicapi;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DynamicSpaceApiSettings {

    @Builder.Default
    private List<DynamicSpaceApiConfig> apis = new ArrayList<>();
}
