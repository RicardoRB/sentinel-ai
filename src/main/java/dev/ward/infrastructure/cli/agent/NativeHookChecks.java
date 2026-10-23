package dev.ward.infrastructure.cli.agent;

import dev.ward.domain.agent.NativeHookInput;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.spec.SpecValidationReport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs a native hook's checks in each project the hook scope selects and collects their findings. A
 * hook started in a project checks only that project, with the findings it always reported; a
 * repository-level hook names the project before each project's findings.
 */
final class NativeHookChecks {
  private NativeHookChecks() {}

  /** Returns the findings of the partial checks for the files {@code event} edited, if any. */
  static Optional<String> afterEdit(
      final HookChecks checks, final NativeHookInput event, final Path directory) {
    final Map<Path, List<Path>> projects =
        checks.scope().editedProjects(directory, NativeHookEditedPaths.of(event, directory));
    final List<String> findings = new ArrayList<>();
    projects.forEach(
        (project, paths) -> {
          final CheckReport report =
              checks.checks().checkPartial(project, paths, SupportedQualityGates.specIds());
          final SpecValidationReport specs =
              event.path() == null
                  ? SpecValidationReport.EMPTY
                  : checks
                      .specs()
                      .validatePaths(
                          project, List.of(), List.of(specPath(event, directory, project)));
          collect(findings, directory, project, report, specs);
        });
    return joined(findings);
  }

  /** Returns the findings of the turn-end checks, if any. */
  static Optional<String> turnEnd(final HookChecks checks, final Path directory) {
    final List<String> findings = new ArrayList<>();
    for (final Path project : checks.scope().turnEndProjects(directory)) {
      collect(
          findings,
          directory,
          project,
          checks.checks().checkExcluding(project, SupportedQualityGates.specIds()),
          checks.specs().validateConfigured(project, List.of()));
    }
    return joined(findings);
  }

  /** The event's path as given in its own project, and resolved for a project below the hook. */
  private static String specPath(
      final NativeHookInput event, final Path directory, final Path project) {
    return project.equals(directory)
        ? event.path()
        : directory.toAbsolutePath().resolve(event.path()).toString();
  }

  private static void collect(
      final List<String> findings,
      final Path directory,
      final Path project,
      final CheckReport report,
      final SpecValidationReport specs) {
    if (report.passed() && !specs.hasFindings()) {
      return;
    }
    final String text = NativeHookMessages.failureText(report, specs);
    findings.add(
        project.equals(directory)
            ? text
            : "Ward checked " + directory.relativize(project) + ":\n" + text);
  }

  private static Optional<String> joined(final List<String> findings) {
    return findings.isEmpty()
        ? Optional.empty()
        : Optional.of(NativeHookMessages.bounded(String.join("\n", findings)));
  }
}
