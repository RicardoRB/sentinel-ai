package dev.ward.domain.agent;

/** An agent that the quality loop can run on a task. */
public interface AgentRunner extends AgentAdapter {
  /** Runs the agent on {@code request} and waits for it to finish. */
  AgentResult run(AgentRequest request);
}
