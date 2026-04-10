package dev.golemcore.brain.domain.auth;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WikiUser {
    String id;
    String username;
    String email;
    String passwordHash;
    UserRole role;
}
