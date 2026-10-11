package dev.sentinel.domain.init;

import java.nio.file.Path;

/** Outbound port for an explicitly approved project-local tool installation. */
@FunctionalInterface
public interface ToolInstaller {
  ToolInstallationResult install(Path projectRoot, String tool);

  default Path installPath(final Path projectRoot, final String tool) {
    final ToolInstallationResult result = install(projectRoot, tool);
    if (!result.installed() || result.executable() == null) {
      throw new IllegalStateException("Tool installation failed: " + result.message());
    }
    return result.executable();
  }

  default void rollback(final Path projectRoot, final ToolInstallationResult result) {}

  default void rollbackPath(final Path projectRoot, final Path executable) {}
}
