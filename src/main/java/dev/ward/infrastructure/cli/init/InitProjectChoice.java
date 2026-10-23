package dev.ward.infrastructure.cli.init;

import dev.ward.application.init.InitTargets;
import dev.ward.domain.config.WardException;
import dev.ward.domain.project.DiscoveredProject;
import dev.ward.domain.project.Project;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/** Turns resolved init targets into the projects to configure, asking when discovery found many. */
final class InitProjectChoice {
  private InitProjectChoice() {}

  /**
   * Returns the projects to configure. Several discovered candidates are asked in the wizard when
   * {@code ask}, and otherwise fail with the {@code --project} values to pass.
   */
  static List<Project> choose(
      final InitTargets targets,
      final Path start,
      final boolean ask,
      final InitWizard wizard,
      final PrintWriter out) {
    final Path base = start.toAbsolutePath().normalize();
    final List<DiscoveredProject> candidates = targets.projects();
    final List<String> ids =
        candidates.stream()
            .map(candidate -> InitTargets.display(base, candidate.project().root()))
            .toList();
    if (!targets.choiceRequired()) {
      if (targets.discovered()) {
        out.println("Discovered project " + ids.getFirst() + ".");
      }
      return candidates.stream().map(DiscoveredProject::project).toList();
    }
    if (!ask) {
      throw new WardException(
          "Found several Maven or Gradle projects. Choose them with --project: "
              + ids.stream().map(id -> "--project " + id).collect(Collectors.joining(" "))
              + ". No files were changed.");
    }
    final List<String> labels =
        candidates.stream().map(candidate -> label(base, candidate)).toList();
    final List<String> chosen = wizard.selectProjects(labels, ids);
    return candidates.stream()
        .filter(candidate -> chosen.contains(ids.get(candidates.indexOf(candidate))))
        .map(DiscoveredProject::project)
        .toList();
  }

  private static String label(final Path base, final DiscoveredProject candidate) {
    final int modules = candidate.moduleCount();
    return InitTargets.display(base, candidate.project().root())
        + " ("
        + candidate.project().buildTool().displayName()
        + (modules == 0 ? "" : ", " + modules + (modules == 1 ? " module" : " modules"))
        + ")";
  }
}
