package dev.sentinel.domain.agent;

/** Stable identity shared by integration-capable and run-capable agent adapters. */
public interface AgentAdapter {
  String id();
}
