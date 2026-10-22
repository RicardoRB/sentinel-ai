package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.AgentAdapter;

/** Identity adapter reserved for Codex integration without gate-engine conditionals. */
public final class CodexAdapter implements AgentAdapter {
  @Override
  public String id() {
    return "codex";
  }
}
