package dev.ward.application.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.ToolInstallationResult;
import dev.ward.domain.init.ToolInstaller;
import dev.ward.domain.project.Project;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Executes explicitly consented gate-tool installations after configuration writes succeed. */
final class GateToolInstallation {
  private static final String OPENSPEC = "openspec";
  private static final Logger LOGGER = Logger.getLogger(GateToolInstallation.class.getName());

  private GateToolInstallation() {}

  static void run(
      final ToolInstaller installer,
      final Project project,
      final InitSelection selection,
      final List<InitGateOption> gates) {
    for (final String requested : selection.installTools()) {
      if (OPENSPEC.equals(requested)) {
        continue;
      }
      final InitGateOption gate =
          gates.stream()
              .filter(candidate -> candidate.id().equals(requested))
              .findFirst()
              .orElse(null);
      if (gate == null) {
        continue;
      }
      final ToolInstallationResult outcome =
          installer.install(project.root(), gate.command().getFirst());
      if (!outcome.installed()) {
        LOGGER.log(
            Level.WARNING,
            () ->
                "event=init-gate-tool-unavailable gate="
                    + requested
                    + " remediation="
                    + outcome.message());
      }
    }
  }
}
