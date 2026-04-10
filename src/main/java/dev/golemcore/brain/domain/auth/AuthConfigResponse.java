package dev.golemcore.brain.domain.auth;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuthConfigResponse {
    boolean authDisabled;
    boolean publicAccess;
    PublicUserView user;
}
