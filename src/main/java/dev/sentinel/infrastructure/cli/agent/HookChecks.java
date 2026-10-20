package dev.sentinel.infrastructure.cli.agent;

import dev.sentinel.application.gate.CheckService;
import dev.sentinel.application.spec.SpecValidationService;

/**
 * The checks a hook runs after an agent edit or at turn end.
 *
 * @param checks runs the configured quality gates
 * @param specs validates OpenSpec artifacts
 */
public record HookChecks(CheckService checks, SpecValidationService specs) {}
