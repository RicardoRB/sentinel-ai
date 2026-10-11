package dev.sentinel.application.init;

import dev.sentinel.domain.init.ToolInstallationResult;
import dev.sentinel.domain.init.ToolInstaller;
import java.nio.file.Path;

/** Default installer used by lightweight callers that do not configure tool installation. */
final class UnavailableToolInstaller implements ToolInstaller {
  @Override
  public ToolInstallationResult install(final Path projectRoot, final String tool) {
    return new ToolInstallationResult(tool, false, null, "Tool installation is not configured.");
  }
}
