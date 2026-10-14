package dev.sentinel.domain.init;

import dev.sentinel.domain.project.Project;

/** Outbound port for reading project facts needed by compatibility resolution. */
@FunctionalInterface
public interface CompatibilityInspection {
  CompatibilityProjectSnapshot inspect(Project project);
}
