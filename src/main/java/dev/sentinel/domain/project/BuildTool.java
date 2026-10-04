package dev.sentinel.domain.project;

public enum BuildTool {
    MAVEN("Maven"), GRADLE("Gradle"), NPM("npm"), PNPM("pnpm"), YARN("Yarn"),
    POETRY("Poetry"), PIP("pip"), GO("Go"), CARGO("Cargo"), DOTNET("dotnet");

    private final String displayName;

    BuildTool(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
