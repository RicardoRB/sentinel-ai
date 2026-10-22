package dev.ward.domain.agent;

/**
 * The outcome of one agent run.
 *
 * @param succeeded whether the agent finished without error
 * @param summary a short description of the outcome
 * @param output the agent's captured output
 */
public record AgentResult(boolean succeeded, String summary, String output) {}
