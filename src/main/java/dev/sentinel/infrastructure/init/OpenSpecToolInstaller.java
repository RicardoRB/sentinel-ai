package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.init.OpenSpecDistribution;
import dev.sentinel.domain.init.ToolInstallationChange;
import dev.sentinel.domain.init.ToolInstallationResult;
import dev.sentinel.domain.init.ToolInstaller;
import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.process.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.inject.Inject;

/** Installs the reviewed OpenSpec distribution into Sentinel-owned project-local storage. */
public final class OpenSpecToolInstaller implements ToolInstaller {
  private static final Logger LOGGER = Logger.getLogger(OpenSpecToolInstaller.class.getName());
  public static final String PACKAGE = OpenSpecDistribution.packageName();
  public static final Path DESTINATION = OpenSpecDistribution.destination(Path.of(""));
  private static final String OWNERSHIP_MARKER = ".sentinel-managed";

  private final CommandExecutor executor;

  @Inject
  public OpenSpecToolInstaller(final CommandExecutor executor) {
    this.executor = executor;
  }

  @Override
  public ToolInstallationResult install(final Path projectRoot, final String tool) {
    if (LOGGER.isLoggable(Level.INFO)) {
      LOGGER.log(Level.INFO, "event=tool-install-start tool=" + tool);
    }
    if (!"openspec".equals(tool)) {
      return new ToolInstallationResult(tool, false, null, "Unsupported tool.");
    }
    return installOpenSpec(projectRoot, tool);
  }

  private ToolInstallationResult installOpenSpec(final Path projectRoot, final String tool) {
    final Path destination = projectRoot.resolve(DESTINATION);
    final Path executable = OpenSpecDistribution.executable(projectRoot);
    final Path staging = projectRoot.resolve(".sentinel/tools/.openspec-staging");
    try {
      final ToolInstallationResult existing =
          existingInstallation(projectRoot, tool, destination, executable);
      if (existing != null) {
        return existing;
      }
      if (Files.exists(staging)) {
        return new ToolInstallationResult(tool, false, null, "OpenSpec staging location is busy.");
      }
      if (!prerequisitesAvailable(projectRoot)) {
        LOGGER.log(Level.WARNING, "event=tool-install-prerequisite-failure tool=openspec");
        return new ToolInstallationResult(
            tool,
            false,
            null,
            "OpenSpec installation requires "
                + OpenSpecDistribution.nodeRequirement()
                + " and npm on PATH.");
      }
      Files.createDirectories(staging);
      final Path stagedExecutable = installPackage(projectRoot, staging);
      if (stagedExecutable == null) {
        LOGGER.log(Level.WARNING, "event=tool-install-failure tool=openspec stage=package");
        deleteStaging(staging);
        return new ToolInstallationResult(
            tool, false, null, "OpenSpec installation failed or did not publish an executable.");
      }
      if (!supportsRequiredValidations(projectRoot, stagedExecutable)) {
        LOGGER.log(Level.WARNING, "event=tool-install-failure tool=openspec stage=validation");
        deleteStaging(staging);
        return new ToolInstallationResult(
            tool, false, null, "Installed OpenSpec does not support the required validations.");
      }
      final Path destinationParent = destination.getParent();
      if (destinationParent == null) {
        deleteStaging(staging);
        return new ToolInstallationResult(
            tool, false, null, "Could not prepare managed destination.");
      }
      Files.createDirectories(destinationParent);
      Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
      Files.writeString(destination.resolve(OWNERSHIP_MARKER), "managed-by: sentinel\n");
      LOGGER.log(Level.INFO, "event=tool-install-complete tool=openspec");
      return new ToolInstallationResult(
          tool,
          true,
          projectRoot.relativize(executable),
          "OpenSpec installed locally.",
          new ToolInstallationChange(null, destination, true));
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "event=tool-install-failure tool=openspec stage=filesystem");
      deleteStaging(staging);
      return new ToolInstallationResult(
          tool, false, null, "Could not prepare managed destination.");
    }
  }

  private ToolInstallationResult existingInstallation(
      final Path projectRoot, final String tool, final Path destination, final Path executable) {
    if (!Files.exists(destination)) {
      return null;
    }
    if (Files.isRegularFile(destination.resolve(OWNERSHIP_MARKER))
        && Files.isExecutable(executable)) {
      return new ToolInstallationResult(
          tool,
          true,
          projectRoot.relativize(executable),
          "Existing managed OpenSpec installation reused.");
    }
    return new ToolInstallationResult(
        tool, false, null, "OpenSpec destination contains unmarked files; refusing to overwrite.");
  }

  private boolean prerequisitesAvailable(final Path projectRoot) {
    final CommandResult node = executor.execute(List.of("node", "--version"), projectRoot);
    final CommandResult npm = executor.execute(List.of("npm", "--version"), projectRoot);
    return node.succeeded() && npm.succeeded() && supportedNode(node.stdout());
  }

  private Path installPackage(final Path projectRoot, final Path staging) {
    final CommandResult result =
        executor.execute(
            List.of("npm", "install", "--prefix", staging.toString(), PACKAGE), projectRoot);
    final Path executable = staging.resolve("node_modules/.bin/openspec");
    return result.succeeded() && Files.isExecutable(executable) ? executable : null;
  }

  private boolean supportsRequiredValidations(final Path projectRoot, final Path executable) {
    final CommandResult help =
        executor.execute(List.of(executable.toString(), "validate", "--help"), projectRoot);
    final String output = help.stdout() + help.stderr();
    return help.succeeded() && output.contains("--archived") && output.contains("--all");
  }

  @Override
  public void rollback(final Path projectRoot, final ToolInstallationResult result) {
    final ToolInstallationChange change = result == null ? null : result.change();
    if (change == null || !change.invocationOwned() || change.published() == null) {
      return;
    }
    final Path destination = change.published();
    if (!destination.startsWith(projectRoot.resolve(".sentinel/tools"))) {
      return;
    }
    deleteStaging(destination);
  }

  @Override
  public void rollbackPath(final Path projectRoot, final Path executable) {
    if (executable == null) {
      return;
    }
    final Path destination = projectRoot.resolve(DESTINATION);
    if (executable.startsWith(DESTINATION)
        && Files.isRegularFile(destination.resolve(OWNERSHIP_MARKER))) {
      deleteStaging(destination);
    }
  }

  private static void deleteStaging(final Path staging) {
    if (!Files.exists(staging)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(staging)) {
      paths
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException ignored) {
                  // Best-effort cleanup must not hide the installation failure.
                }
              });
    } catch (IOException ignored) {
      // Best-effort cleanup must not hide the installation failure.
    }
  }

  private static boolean supportedNode(final String version) {
    try {
      final String value = version.trim().replaceFirst("^v", "");
      final String[] parts = value.split("\\.");
      final int major = Integer.parseInt(parts[0]);
      final int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
      return major > 20 || (major == 20 && minor >= 19);
    } catch (NumberFormatException ignored) {
      return false;
    }
  }
}
