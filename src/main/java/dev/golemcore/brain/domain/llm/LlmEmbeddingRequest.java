package dev.golemcore.brain.domain.llm;

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
public class LlmEmbeddingRequest {
    private LlmProviderConfig provider;
    private LlmModelConfig model;

    @Builder.Default
    private List<String> inputs = new ArrayList<>();
}
