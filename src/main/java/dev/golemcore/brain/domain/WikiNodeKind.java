package dev.golemcore.brain.domain;

public enum WikiNodeKind {
    ROOT,
    SECTION,
    PAGE;

    public boolean isContainer() {
        return this == ROOT || this == SECTION;
    }
}
