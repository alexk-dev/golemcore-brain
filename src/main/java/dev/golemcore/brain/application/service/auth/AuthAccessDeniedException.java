package dev.golemcore.brain.application.service.auth;

public class AuthAccessDeniedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AuthAccessDeniedException(String message) {
        super(message);
    }
}
