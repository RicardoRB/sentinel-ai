package dev.ward.infrastructure.cli.init;

import dev.ward.application.init.InitService;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.InitPlan;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.cli.ExitCodes;
import java.io.PrintWriter;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Plans a resolved selection, then previews, reviews, or applies it. */
final class InitApply {
  private final InitService init;
  private final InitConfirmations confirmations;
  private final PrintWriter out;
  private final InitResultPrinter printer;

  InitApply(final InitService init, final InitConfirmations confirmations, final PrintWriter out) {
    this.init = init;
    this.confirmations = confirmations;
    this.out = out;
    this.printer = new InitResultPrinter(out);
  }

  int run(final Project project, final InitSelection selection, final Mode mode) {
    final InitPlan plan = init.plan(project.root(), selection, mode.approved());
    if (mode.dryRun()) {
      printer.printPlan(plan, project.root());
      return ExitCodes.OK;
    }
    if (plan.hasConflict()) {
      printer.printPlan(plan, project.root());
      throw new WardException(conflictMessage(plan, project));
    }
    if (mode.review()) {
      printer.printPlan(plan, project.root());
      if (!confirmations.review()) {
        out.println("No changes made.");
        return ExitCodes.OK;
      }
    }
    final Predicate<CompatibilityFinding> approval =
        finding -> mode.approved() || confirmations.compatibilityUpdate(finding);
    printer.print(init.initialize(project.root(), selection, mode.overwrite(), approval));
    return ExitCodes.OK;
  }

  private static String conflictMessage(final InitPlan plan, final Project project) {
    final String conflicts =
        plan.files().stream()
            .filter(file -> file.action() == PlannedFileChange.Action.CONFLICT)
            .map(
                file ->
                    file.details().isEmpty()
                        ? project.root().relativize(file.file()) + " is user-owned"
                        : String.join("; ", file.details()))
            .collect(Collectors.joining(", "));
    return "Cannot initialize "
        + project.root()
        + ": the selected changes conflict with user-owned content ("
        + conflicts
        + "). No files were changed.";
  }

  /**
   * How to finish: preview only, ask before writing, whether compatibility updates are approved,
   * and whether an existing configuration may be replaced.
   */
  record Mode(boolean dryRun, boolean review, boolean approved, boolean overwrite) {}
}
