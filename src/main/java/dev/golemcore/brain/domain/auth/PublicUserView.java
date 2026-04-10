package dev.golemcore.brain.domain.auth;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PublicUserView {
    String id;
    String username;
    String email;
    UserRole role;
}
