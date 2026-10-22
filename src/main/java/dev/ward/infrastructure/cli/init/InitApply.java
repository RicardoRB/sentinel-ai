package dev.ward.infrastructure.cli.init;

import dev.ward.application.init.InitService;
import dev.ward.application.init.InitTargets;
import dev.ward.application.init.MultiProjectPlan;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.InitPlan;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.cli.ExitCodes;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

  /**
   * Plans every target, shows one grouped plan, asks once, then applies the targets in order. A
   * failure names the projects already configured, which keep their changes.
   */
  int runAll(final List<Target> targets, final Mode mode, final Path base) {
    final List<InitPlan> plans =
        targets.stream()
            .map(target -> init.plan(target.project().root(), target.selection(), mode.approved()))
            .toList();
    final MultiProjectPlan plan =
        MultiProjectPlan.of(targets.stream().map(Target::project).toList(), plans);
    final MultiProjectPlanPrinter planPrinter = new MultiProjectPlanPrinter(out);
    if (mode.dryRun() || plan.hasConflict() || mode.review()) {
      planPrinter.print(plan, base);
    }
    if (mode.dryRun()) {
      return ExitCodes.OK;
    }
    if (plan.hasConflict()) {
      throw new WardException(
          "Cannot initialize: the selected changes conflict with user-owned content. No files"
              + " were changed.");
    }
    if (mode.review() && !confirmations.review()) {
      out.println("No changes made.");
      return ExitCodes.OK;
    }
    applyAll(targets, mode, base);
    return ExitCodes.OK;
  }

  private void applyAll(final List<Target> targets, final Mode mode, final Path base) {
    final Predicate<CompatibilityFinding> approval =
        finding -> mode.approved() || confirmations.compatibilityUpdate(finding);
    final List<String> configured = new ArrayList<>();
    for (final Target target : targets) {
      final String name = InitTargets.display(base, target.project().root());
      try {
        printer.print(
            init.initialize(
                target.project().root(), target.selection(), target.overwrite(), approval));
      } catch (WardException | UncheckedIOException | IllegalStateException failure) {
        throw new WardException(
            "Could not initialize "
                + name
                + ": "
                + failure.getMessage()
                + (configured.isEmpty()
                    ? ""
                    : " Already configured: " + String.join(", ", configured) + "."),
            failure);
      }
      configured.add(name);
    }
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

  /** One project of a multi-project run, with the answers it applies and whether it replaces. */
  record Target(Project project, InitSelection selection, boolean overwrite) {}
}
