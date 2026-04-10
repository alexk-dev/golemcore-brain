package dev.golemcore.brain.application.service.auth;

public class AuthAccessDeniedException extends RuntimeException {

    public AuthAccessDeniedException(String message) {
        super(message);
    }
}
