package dev.golemcore.brain.domain.auth;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class UserSession {
    String token;
    String userId;
    Instant expiresAt;
}
