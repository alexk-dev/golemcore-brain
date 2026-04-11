package dev.golemcore.brain.application.service.auth;

public class AuthUnauthorizedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AuthUnauthorizedException(String message) {
        super(message);
    }
}
