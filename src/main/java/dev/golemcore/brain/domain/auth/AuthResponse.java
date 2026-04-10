package dev.golemcore.brain.domain.auth;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuthResponse {
    String message;
    PublicUserView user;
}
