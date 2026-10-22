package dev.ward.domain.loop;

/** Why a quality loop stopped. */
public enum LoopTerminalState {
  PASSED,
  MAX_ITERATIONS_REACHED,
  TIMEOUT,
  AGENT_ERROR,
  GATE_ERROR
}
