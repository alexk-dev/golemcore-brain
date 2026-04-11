package dev.golemcore.brain.application.service.index;

import dev.golemcore.brain.application.port.out.LlmEmbeddingPort;
import dev.golemcore.brain.domain.llm.LlmEmbeddingRequest;
import dev.golemcore.brain.domain.llm.LlmEmbeddingResponse;
import java.util.List;

class NoopLlmEmbeddingPort implements LlmEmbeddingPort {

    @Override
    public LlmEmbeddingResponse embed(LlmEmbeddingRequest request) {
        return LlmEmbeddingResponse.builder()
                .embeddings(List.of())
                .build();
    }
}
