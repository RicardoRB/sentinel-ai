package dev.ward.application.gate;

import dev.ward.domain.gate.GateStatus;

/**
 * The revision an OpenAPI diff gate compares with, or why it stops without comparing.
 *
 * @param commit the baseline commit ID, or {@code null} when the gate stops
 * @param status the status of a stopped gate
 * @param summary why the gate stopped
 * @param reason a short log reason for a stopped gate
 */
record OpenApiBaseline(String commit, GateStatus status, String summary, String reason) {
  /** A baseline to compare with. */
  static OpenApiBaseline at(final String commit) {
    return new OpenApiBaseline(commit, null, null, null);
  }

  /** A gate that stops with {@code status} and {@code summary}, logged as {@code reason}. */
  static OpenApiBaseline stop(final GateStatus status, final String summary, final String reason) {
    return new OpenApiBaseline(null, status, summary, reason);
  }

  /** Returns whether there is a commit to compare with. */
  boolean found() {
    return commit != null;
  }
}
