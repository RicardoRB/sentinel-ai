package dev.ward.application.gate;

import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;

/** Results of OpenAPI gates that stop before running their tool. */
final class OpenApiResults {
  private OpenApiResults() {}

  /** A result with {@code status} and {@code summary} and no process output. */
  static GateResult stopped(final String name, final GateStatus status, final String summary) {
    return GateResult.builder(name, status).summary(summary).build();
  }

  /** The summary for a configured spec that is not in the working tree. */
  static String missingSpec(final String spec) {
    return "The OpenAPI spec " + spec + " does not exist.";
  }
}
