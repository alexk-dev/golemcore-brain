package dev.golemcore.brain.service;

public class WikiNotFoundException extends RuntimeException {

    public WikiNotFoundException(String message) {
        super(message);
    }
}
