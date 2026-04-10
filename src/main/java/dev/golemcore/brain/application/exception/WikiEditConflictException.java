package dev.golemcore.brain.application.exception;

import dev.golemcore.brain.domain.WikiPageDocument;

public class WikiEditConflictException extends RuntimeException {

    private final String expectedRevision;
    private final WikiPageDocument currentPage;

    public WikiEditConflictException(String expectedRevision, WikiPageDocument currentPage) {
        super("This page was updated in another session. Reload the latest version or merge your draft.");
        this.expectedRevision = expectedRevision;
        this.currentPage = currentPage;
    }

    public String getExpectedRevision() {
        return expectedRevision;
    }

    public String getCurrentRevision() {
        return currentPage.getRevision();
    }

    public WikiPageDocument getCurrentPage() {
        return currentPage;
    }
}
