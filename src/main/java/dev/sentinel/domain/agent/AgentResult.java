package dev.sentinel.domain.agent;

public record AgentResult(boolean succeeded, String summary, String output) {}
