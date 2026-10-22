package dev.ward.infrastructure.cli.agent;

import dev.ward.application.gate.CheckService;
import dev.ward.application.spec.SpecValidationService;

/**
 * The checks a hook runs after an agent edit or at turn end.
 *
 * @param checks runs the configured quality gates
 * @param specs validates OpenSpec artifacts
 */
public record HookChecks(CheckService checks, SpecValidationService specs) {}
