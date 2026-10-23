package dev.ward.infrastructure.cli.agent;

import dev.ward.application.agent.HookScope;
import dev.ward.application.gate.CheckService;
import dev.ward.application.spec.SpecValidationService;

/**
 * The checks a hook runs after an agent edit or at turn end.
 *
 * @param checks runs the configured quality gates
 * @param specs validates OpenSpec artifacts
 * @param scope decides which projects a hook checks
 */
public record HookChecks(CheckService checks, SpecValidationService specs, HookScope scope) {
  /** Creates checks that always check the hook's own project. */
  public HookChecks(final CheckService checks, final SpecValidationService specs) {
    this(checks, specs, HookScope.ALWAYS);
  }
}
