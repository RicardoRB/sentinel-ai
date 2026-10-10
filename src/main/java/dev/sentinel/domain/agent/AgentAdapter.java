package dev.sentinel.domain.agent;

/**
 * Stable identity shared by integration-capable and run-capable agent adapters. It is a base
 * contract for agent capabilities to extend, not a lambda target.
 */
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface AgentAdapter {
  String id();
}
