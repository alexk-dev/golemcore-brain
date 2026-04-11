package dev.golemcore.brain.application.port.out;

import dev.golemcore.brain.domain.llm.LlmEmbeddingRequest;
import dev.golemcore.brain.domain.llm.LlmEmbeddingResponse;

public interface LlmEmbeddingPort {
    LlmEmbeddingResponse embed(LlmEmbeddingRequest request);
}
