package dev.sentinel.domain.agent;

import java.util.List;

public record IntegrationResult(Status status, List<String> changed, String message) {
    public enum Status { CHANGED, ALREADY_PRESENT, REMOVED, CONFLICT, NOT_FOUND }

    public IntegrationResult {
        changed = List.copyOf(changed);
    }
}
