package dev.sentinel.application.gate;

import dev.sentinel.domain.gate.GateResult;

/** Receives ordered lifecycle events as quality gates execute. */
public interface CheckProgressListener {
  CheckProgressListener NO_OP = new CheckProgressListener() {};

  default void gateStarted(String name) {
    // Optional callback.
  }

  default void gateFinished(GateResult result) {
    // Optional callback.
  }
}
