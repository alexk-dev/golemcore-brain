package dev.golemcore.brain.application.port.out;

import dev.golemcore.brain.domain.apikey.ApiKey;
import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository {

    void initialize();

    List<ApiKey> listAll();

    List<ApiKey> listBySpace(String spaceId);

    Optional<ApiKey> findById(String id);

    ApiKey save(ApiKey apiKey);

    void delete(String id);
}
