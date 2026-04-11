package dev.golemcore.brain.application.port.out;

import dev.golemcore.brain.domain.llm.LlmSettings;

public interface LlmSettingsRepository {
    void initialize();

    LlmSettings load();

    LlmSettings save(LlmSettings settings);
}
