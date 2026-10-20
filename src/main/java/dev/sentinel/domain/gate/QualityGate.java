package dev.sentinel.domain.gate;

import dev.sentinel.domain.project.Project;

/**
 * A configured check that runs against a project; {@code QualityGateFactory} creates one per gate
 * ID.
 */
public interface QualityGate {

  /** Returns the gate ID from {@code sentinel.toml}. */
  String name();

  /** Runs the gate against {@code project} and returns its result. */
  GateResult execute(Project project);
}
