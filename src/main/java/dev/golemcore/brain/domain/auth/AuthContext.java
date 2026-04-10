package dev.golemcore.brain.domain.auth;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AuthContext {
    boolean authDisabled;
    boolean publicAccess;
    boolean authenticated;
    PublicUserView user;

    public boolean isReadOnlyAnonymous() {
        return !authDisabled && !authenticated && publicAccess;
    }

    public boolean canEdit() {
        if (authDisabled) {
            return true;
        }
        return user != null && user.getRole().canEdit();
    }

    public boolean canManageUsers() {
        if (authDisabled) {
            return true;
        }
        return user != null && user.getRole().canManageUsers();
    }
}
