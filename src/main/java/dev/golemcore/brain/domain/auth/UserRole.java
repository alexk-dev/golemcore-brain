package dev.golemcore.brain.domain.auth;

public enum UserRole {
    ADMIN,
    EDITOR,
    VIEWER;

    public boolean canEdit() {
        return this == ADMIN || this == EDITOR;
    }

    public boolean canManageUsers() {
        return this == ADMIN;
    }
}
