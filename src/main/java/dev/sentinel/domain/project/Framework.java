package dev.sentinel.domain.project;

public enum Framework {
    SPRING_BOOT("Spring Boot"),
    NONE("None");

    private final String displayName;

    Framework(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
