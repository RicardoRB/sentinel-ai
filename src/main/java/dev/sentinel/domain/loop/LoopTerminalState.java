package dev.sentinel.domain.loop;

public enum LoopTerminalState {
  PASSED,
  MAX_ITERATIONS_REACHED,
  TIMEOUT,
  AGENT_ERROR,
  GATE_ERROR
}
