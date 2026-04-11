package dev.golemcore.brain.application.exception;

public class WikiNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public WikiNotFoundException(String message) {
        super(message);
    }
}
