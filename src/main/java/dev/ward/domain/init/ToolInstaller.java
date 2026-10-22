package dev.ward.domain.init;

import java.nio.file.Path;

/** Outbound port for an explicitly approved project-local tool installation. */
@FunctionalInterface
public interface ToolInstaller {
  /** Installs {@code tool} under {@code projectRoot} and reports the outcome. */
  ToolInstallationResult install(Path projectRoot, String tool);

  /**
   * Installs {@code tool} and returns its executable.
   *
   * @throws IllegalStateException if the installation fails
   */
  default Path installPath(final Path projectRoot, final String tool) {
    final ToolInstallationResult result = install(projectRoot, tool);
    if (!result.installed() || result.executable() == null) {
      throw new IllegalStateException("Tool installation failed: " + result.message());
    }
    return result.executable();
  }

  /** Undoes the installation {@code result} describes; the default does nothing. */
  default void rollback(final Path projectRoot, final ToolInstallationResult result) {}

  /** Removes the tool installed at {@code executable}; the default does nothing. */
  default void rollbackPath(final Path projectRoot, final Path executable) {}
}
