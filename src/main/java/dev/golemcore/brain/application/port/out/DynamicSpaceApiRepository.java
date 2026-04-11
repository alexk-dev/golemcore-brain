package dev.golemcore.brain.application.port.out;

import dev.golemcore.brain.domain.dynamicapi.DynamicSpaceApiSettings;

public interface DynamicSpaceApiRepository {
    DynamicSpaceApiSettings load(String spaceId);

    DynamicSpaceApiSettings save(String spaceId, DynamicSpaceApiSettings settings);
}
