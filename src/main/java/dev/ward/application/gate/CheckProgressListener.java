package dev.ward.application.gate;

import dev.ward.domain.gate.GateResult;

/** Receives ordered lifecycle events as quality gates execute. */
public interface CheckProgressListener {
  CheckProgressListener NO_OP = new CheckProgressListener() {};

  /** Called before gate {@code name} runs; the default does nothing. */
  default void gateStarted(String name) {
    // Optional callback.
  }

  /** Called after a gate finished with {@code result}; the default does nothing. */
  default void gateFinished(GateResult result) {
    // Optional callback.
  }
}
