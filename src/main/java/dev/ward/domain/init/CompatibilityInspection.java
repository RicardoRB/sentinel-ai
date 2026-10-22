package dev.ward.domain.init;

import dev.ward.domain.project.Project;

/** Outbound port for reading project facts needed by compatibility resolution. */
@FunctionalInterface
public interface CompatibilityInspection {
  /** Reads the toolchain and declared plugin versions of {@code project}. */
  CompatibilityProjectSnapshot inspect(Project project);
}
