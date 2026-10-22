package dev.ward.application.doctor;

import dev.ward.application.doctor.DoctorService.Finding;
import dev.ward.application.doctor.DoctorService.Status;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.doctor.ExecutableObservation;
import dev.ward.domain.doctor.OpenSpecIgnoreWarning;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Diagnoses the prerequisites of an enabled OpenSpec gate, honouring its configured root. */
final class OpenSpecDoctorFindings {
  private OpenSpecDoctorFindings() {}

  /** Returns the findings for one enabled OpenSpec gate of the project at {@code root}. */
  static List<Finding> of(
      final EnvironmentInspection environment,
      final Path root,
      final Map.Entry<String, GateConfiguration> entry) {
    final List<Finding> findings = new ArrayList<>();
    final String executable =
        entry.getValue().command().isEmpty() ? "" : entry.getValue().command().getFirst();
    final String configured = entry.getValue().root();
    final Path openSpecRoot =
        configured == null ? root : root.resolve(configured).toAbsolutePath().normalize();
    final String where =
        configured == null ? "project-root" : "configured root '" + configured + "'";
    final ExecutableObservation observation =
        environment.inspectExecutable(openSpecRoot, executable);
    final boolean directory = environment.hasOpenSpecDirectory(openSpecRoot);
    if (observation.usable() && directory) {
      findings.add(
          new Finding(
              entry.getKey(),
              Status.OK,
              "OpenSpec executable and project directory are available."));
      if (environment.isOpenSpecDirectoryIgnored(openSpecRoot)) {
        findings.add(new Finding(entry.getKey(), Status.WARNING, OpenSpecIgnoreWarning.MESSAGE));
      }
      return findings;
    }
    final String executableMessage =
        observation.usable() ? "executable available" : "executable unavailable";
    final String directoryMessage =
        directory
            ? where + " openspec/ directory available"
            : where + " openspec/ directory missing";
    findings.add(
        new Finding(
            entry.getKey(),
            Status.ERROR,
            executableMessage
                + "; "
                + directoryMessage
                + ". Configure prerequisites before checking."));
    return findings;
  }
}
