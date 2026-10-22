package dev.ward.domain.gate;

/** The outcome of one gate. */
public enum GateStatus {
  PASSED,
  FAILED,
  SKIPPED,
  UNAVAILABLE,
  EXECUTION_ERROR
}
