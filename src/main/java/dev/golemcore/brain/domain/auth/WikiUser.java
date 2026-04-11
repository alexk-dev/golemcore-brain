package dev.golemcore.brain.domain.auth;

import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class WikiUser {
    String id;
    String username;
    String email;
    String passwordHash;
    /**
     * Legacy single-role field kept for compatibility; use memberships for
     * authorization.
     */
    UserRole role;
    @Singular
    List<SpaceMembership> memberships;

    public boolean isGlobalAdmin() {
        return memberships.stream().anyMatch(m -> m.isGlobal() && m.getRole() == UserRole.ADMIN);
    }

    public Optional<UserRole> effectiveRole(String spaceId) {
        UserRole best = null;
        for (SpaceMembership membership : memberships) {
            if (membership.isGlobal() || membership.getSpaceId().equals(spaceId)) {
                if (best == null || rank(membership.getRole()) > rank(best)) {
                    best = membership.getRole();
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static int rank(UserRole role) {
        return switch (role) {
        case ADMIN -> 3;
        case EDITOR -> 2;
        case VIEWER -> 1;
        };
    }
}
