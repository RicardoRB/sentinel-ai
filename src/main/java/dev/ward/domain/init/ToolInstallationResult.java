package dev.ward.domain.init;

import java.nio.file.Path;

/** Outcome of a project-local tool installation attempt. */
public record ToolInstallationResult(
    String tool,
    boolean installed,
    Path executable,
    String message,
    ToolInstallationChange change) {
  /** Creates a result without a rollback handle. */
  public ToolInstallationResult(
      final String tool, final boolean installed, final Path executable, final String message) {
    this(tool, installed, executable, message, null);
  }
}
