package dev.golemcore.brain.domain;

public enum WikiNodeKind {
    ROOT, SECTION, PAGE;

    public boolean isContainer() {
        return this == ROOT || this == SECTION;
    }

    public boolean keepsHistory() {
        return this == PAGE || this == SECTION;
    }
}
