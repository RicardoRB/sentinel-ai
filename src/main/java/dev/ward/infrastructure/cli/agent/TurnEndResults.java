package dev.ward.infrastructure.cli.agent;

import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.spec.SpecValidationReport;

/**
 * What the turn-end check found.
 *
 * @param report the quality-gate report
 * @param specs the OpenSpec validation report
 */
record TurnEndResults(CheckReport report, SpecValidationReport specs) {

  /** Whether every gate passed and no spec finding was reported. */
  boolean clean() {
    return report.passed() && !specs.hasFindings();
  }
}
