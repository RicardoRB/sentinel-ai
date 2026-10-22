package dev.ward.infrastructure.cli.init;

import dev.ward.application.init.InitTargets;
import dev.ward.application.init.MultiProjectPlan;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.project.Project;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.List;

/** Prints the plan of a run that configures several projects, grouped by project. */
final class MultiProjectPlanPrinter {
  private final PrintWriter out;
  private final InitResultPrinter files;

  MultiProjectPlanPrinter(final PrintWriter out) {
    this.out = out;
    this.files = new InitResultPrinter(out);
  }

  /**
   * Prints each project's own files relative to its root, then the repository-level files once,
   * relative to the repository root. Project headings are relative to {@code base}.
   */
  void print(final MultiProjectPlan plan, final Path base) {
    out.println("Ward will make these changes:");
    for (int i = 0; i < plan.projects().size(); i++) {
      final Project project = plan.projects().get(i);
      out.println();
      out.println("Project " + InitTargets.display(base, project.root()) + ":");
      plan.projectFiles(i).forEach(file -> files.printFile(file, project.root()));
      files.printInstallations(plan.plans().get(i), project.root());
    }
    final List<PlannedFileChange> shared =
        plan.shared().stream()
            .filter(file -> file.action() != PlannedFileChange.Action.UNCHANGED)
            .toList();
    if (!shared.isEmpty()) {
      final Path repository = plan.projects().getFirst().repositoryRoot();
      out.println();
      out.println("Repository root:");
      shared.forEach(file -> files.printFile(file, repository));
    }
    out.println();
  }
}
