package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.AgentAdapter;

/** Identity adapter reserved for Claude Code integration without gate-engine conditionals. */
public final class ClaudeCodeAdapter implements AgentAdapter {
  @Override
  public String id() {
    return "claude-code";
  }
}
