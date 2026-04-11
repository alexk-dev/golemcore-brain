package dev.golemcore.brain.domain.apikey;

import dev.golemcore.brain.domain.auth.UserRole;
import java.time.Instant;
import java.util.Set;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ApiKey {
    /** JWT id (jti). */
    String id;
    String name;
    /** Owning user id, or "service:xxx" for machine keys. */
    String subject;
    /** Null = global key (access to all spaces). */
    String spaceId;
    /** Permissions granted by this key within its scope. */
    Set<UserRole> roles;
    Instant createdAt;
    /** Null = non-expiring. */
    Instant expiresAt;
    boolean revoked;

    public boolean isGlobal() {
        return spaceId == null;
    }
}
