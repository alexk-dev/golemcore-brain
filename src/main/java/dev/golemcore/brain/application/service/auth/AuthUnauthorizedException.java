package dev.golemcore.brain.application.service.auth;

public class AuthUnauthorizedException extends RuntimeException {

    public AuthUnauthorizedException(String message) {
        super(message);
    }
}
