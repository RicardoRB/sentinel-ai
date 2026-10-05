package dev.sentinel.domain.agent;

public interface AgentRunner extends AgentAdapter {
  AgentResult run(AgentRequest request);
}
