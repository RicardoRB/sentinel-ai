package dev.ward.infrastructure.cli.init;

import dev.ward.application.init.InitService;
import dev.ward.application.init.InitSetupCatalog;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.project.Project;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the answers of every project a run configures: the first project is asked, the others
 * reuse its answers, and a project whose existing configuration the user keeps is left out.
 */
final class InitTargetAnswers {
  private final InitQuestions.Answers answers;
  private final InitPrompts prompts;
  private final boolean terminal;
  private final boolean overwrite;
  private final PrintWriter out;

  InitTargetAnswers(
      final InitQuestions.Answers answers,
      final InitPrompts prompts,
      final boolean terminal,
      final boolean overwrite,
      final PrintWriter out) {
    this.answers = answers;
    this.prompts = prompts;
    this.terminal = terminal;
    this.overwrite = overwrite;
    this.out = out;
  }

  List<InitApply.Target> resolve(final InitService init, final List<Project> projects) {
    final List<InitApply.Target> targets = new ArrayList<>();
    final boolean several = projects.size() > 1;
    for (final Project project : projects) {
      if (several) {
        out.println("Project " + project.root() + ":");
      }
      target(init, project, targets).ifPresent(targets::add);
    }
    return targets;
  }

  private Optional<InitApply.Target> target(
      final InitService init, final Project project, final List<InitApply.Target> earlier) {
    final InitSetupCatalog catalog = init.catalog();
    final InitQuestions questions = new InitQuestions(answers, prompts, catalog, project, terminal);
    final boolean exists = init.configurationExists(project);
    if (exists && !overwrite && !questions.confirmOverwrite(project.root().resolve("ward.toml"))) {
      out.println("Kept existing ward.toml. No changes were made.");
      return Optional.empty();
    }
    if (exists && earlier.isEmpty()) {
      catalog.previousChoices(project).ifPresent(questions::reconfigure);
    }
    final InitSelection selection =
        earlier.isEmpty() ? questions.resolve() : earlier.getFirst().selection();
    new InitResultPrinter(out).printSelectedGates(catalog, project, selection.gates());
    return Optional.of(new InitApply.Target(project, selection, overwrite || exists));
  }
}
