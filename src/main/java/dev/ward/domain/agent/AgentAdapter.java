package dev.ward.domain.agent;

/**
 * Stable identity shared by integration-capable and run-capable agent adapters. It is a base
 * contract for agent capabilities to extend, not a lambda target.
 */
@FunctionalInterface
public interface AgentAdapter {
  /** Returns the agent ID used on the command line, such as {@code claude-code}. */
  String id();
}
