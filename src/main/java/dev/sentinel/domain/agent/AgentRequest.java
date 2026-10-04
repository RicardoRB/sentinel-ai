package dev.sentinel.domain.agent;

public record AgentRequest(String task, int timeoutSeconds) {
    public AgentRequest {
        if (task == null || task.isBlank()) throw new IllegalArgumentException("task must not be blank");
        if (timeoutSeconds < 1) throw new IllegalArgumentException("timeoutSeconds must be positive");
    }
}
