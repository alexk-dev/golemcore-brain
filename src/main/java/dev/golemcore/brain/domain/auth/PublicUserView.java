package dev.golemcore.brain.domain.auth;

import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class PublicUserView {
    String id;
    String username;
    String email;
    UserRole role;
    @Singular
    List<SpaceMembership> memberships;
}
