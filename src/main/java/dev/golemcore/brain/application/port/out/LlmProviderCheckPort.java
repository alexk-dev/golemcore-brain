package dev.golemcore.brain.application.port.out;

import dev.golemcore.brain.domain.llm.LlmProviderCheckResult;
import dev.golemcore.brain.domain.llm.LlmProviderConfig;

public interface LlmProviderCheckPort {
    LlmProviderCheckResult check(String providerName, LlmProviderConfig providerConfig);
}
