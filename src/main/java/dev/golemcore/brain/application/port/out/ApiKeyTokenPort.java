package dev.golemcore.brain.application.port.out;

import dev.golemcore.brain.domain.apikey.ApiKey;
import dev.golemcore.brain.domain.auth.UserRole;
import java.time.Instant;
import java.util.Set;

public interface ApiKeyTokenPort {

    String issue(ApiKey apiKey);

    ParsedApiKeyToken parse(String token);

    record ParsedApiKeyToken(
            String jti,
            String subject,
            String spaceId,
            Set<UserRole> roles,
            Instant expiresAt) {
    }
}
