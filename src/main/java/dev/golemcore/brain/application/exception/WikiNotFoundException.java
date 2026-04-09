package dev.golemcore.brain.application.exception;

public class WikiNotFoundException extends RuntimeException {

    public WikiNotFoundException(String message) {
        super(message);
    }
}
