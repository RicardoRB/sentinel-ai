package dev.sentinel.domain.agent;

/** Read-only observations of the human-owned OpenSpec setup that guidance depends on. */
public record OpenSpecSetup(
    boolean executableOnPath, boolean projectDirectory, boolean verifyWorkflow) {}
