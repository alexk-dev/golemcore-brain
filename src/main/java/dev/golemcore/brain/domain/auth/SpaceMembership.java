package dev.golemcore.brain.domain.auth;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SpaceMembership {
    /** Null means global membership (applies to all spaces). */
    String spaceId;
    UserRole role;

    public boolean isGlobal() {
        return spaceId == null;
    }
}
