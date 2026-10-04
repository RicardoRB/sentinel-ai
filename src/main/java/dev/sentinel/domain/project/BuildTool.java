package dev.sentinel.domain.project;

public enum BuildTool {
    MAVEN("Maven");

    private final String displayName;

    BuildTool(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
