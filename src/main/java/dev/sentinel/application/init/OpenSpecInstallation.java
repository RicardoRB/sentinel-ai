package dev.sentinel.application.init;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.InitSelection;
import dev.sentinel.domain.init.ToolInstaller;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;

/** Runs the OpenSpec installation a selection consented to. */
final class OpenSpecInstallation {
  static final String TOOL = "openspec";

  private OpenSpecInstallation() {}

  /** Installs OpenSpec when consented and returns its executable, or {@code null} when skipped. */
  static Path install(
      final ToolInstaller installer, final Project project, final InitSelection selection) {
    if (selection.installTools().isEmpty()) {
      return null;
    }
    if (!selection.tools().contains(TOOL)) {
      throw new SentinelException(
          "Installation consent requires selecting the corresponding tool.");
    }
    try {
      return installer.installPath(project.root(), TOOL);
    } catch (IllegalStateException e) {
      throw new SentinelException("OpenSpec installation failed: " + e.getMessage(), e);
    }
  }

  /** The command that runs OpenSpec: the installed executable, or {@code openspec} on PATH. */
  static String executable(final Path installed) {
    return installed == null ? TOOL : installed.toString();
  }
}
