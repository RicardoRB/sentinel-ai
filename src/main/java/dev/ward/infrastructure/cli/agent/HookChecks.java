package dev.ward.infrastructure.cli.agent;

import dev.ward.application.agent.TurnEndScope;
import dev.ward.application.gate.CheckService;
import dev.ward.application.spec.SpecValidationService;

/**
 * The checks a hook runs after an agent edit or at turn end.
 *
 * @param checks runs the configured quality gates
 * @param specs validates OpenSpec artifacts
 * @param turnEnd decides whether a turn-end check runs
 */
public record HookChecks(CheckService checks, SpecValidationService specs, TurnEndScope turnEnd) {
  /** Creates checks whose turn-end check always runs. */
  public HookChecks(final CheckService checks, final SpecValidationService specs) {
    this(checks, specs, TurnEndScope.ALWAYS);
  }
}
