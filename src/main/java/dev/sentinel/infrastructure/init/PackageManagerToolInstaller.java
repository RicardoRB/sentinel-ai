package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.init.GateToolInstallCatalog;
import dev.sentinel.domain.init.ToolInstallationResult;
import dev.sentinel.domain.init.ToolInstaller;
import dev.sentinel.domain.process.CommandExecutor;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/** Installs a gate executable through the first supported package manager on PATH. */
public final class PackageManagerToolInstaller implements ToolInstaller {
  private static final Logger LOGGER =
      Logger.getLogger(PackageManagerToolInstaller.class.getName());
  private final CommandExecutor executor;
  private final EnvironmentInspection environment;

  @Inject
  public PackageManagerToolInstaller(
      final CommandExecutor executor, final EnvironmentInspection environment) {
    this.executor = executor;
    this.environment = environment;
  }

  @Override
  public ToolInstallationResult install(final Path projectRoot, final String tool) {
    final GateToolInstallCatalog.Entry entry =
        GateToolInstallCatalog.forExecutable(tool).orElse(null);
    if (entry == null) {
      return failure(tool, "No supported installation method. ");
    }
    final GateToolInstallCatalog.InstallMethod method =
        entry.methods().stream()
            .filter(
                candidate -> environment.inspect(projectRoot).hasExecutable(candidate.manager()))
            .findFirst()
            .orElse(null);
    if (method == null) {
      return failure(tool, "Install manually: " + entry.manualGuidance());
    }
    if (LOGGER.isLoggable(Level.INFO)) {
      LOGGER.log(
          Level.INFO, "event=tool-install-start tool=" + tool + " manager=" + method.manager());
    }
    final boolean succeeded = executor.execute(method.arguments(), projectRoot).succeeded();
    final boolean available = environment.inspect(projectRoot).hasExecutable(tool);
    if (succeeded && available) {
      if (LOGGER.isLoggable(Level.INFO)) {
        LOGGER.log(
            Level.INFO,
            "event=tool-install-complete tool=" + tool + " manager=" + method.manager());
      }
      return new ToolInstallationResult(
          tool, true, null, "Installed with " + method.manager() + ".");
    }
    if (LOGGER.isLoggable(Level.WARNING)) {
      LOGGER.log(
          Level.WARNING,
          "event=tool-install-failure tool=" + tool + " manager=" + method.manager());
    }
    return failure(
        tool,
        "Could not install " + tool + " with " + method.manager() + ". " + entry.manualGuidance());
  }

  private static ToolInstallationResult failure(final String tool, final String message) {
    return new ToolInstallationResult(tool, false, null, message);
  }
}
