package dev.sentinel.domain.project;

public enum Language {
    JAVA("Java");

    private final String displayName;

    Language(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
