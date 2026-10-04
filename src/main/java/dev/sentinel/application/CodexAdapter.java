package dev.sentinel.application;

import dev.sentinel.domain.agent.AgentAdapter;

/** Identity adapter reserved for Codex integration without gate-engine conditionals. */
public final class CodexAdapter implements AgentAdapter {
    @Override public String id() { return "codex"; }
}
