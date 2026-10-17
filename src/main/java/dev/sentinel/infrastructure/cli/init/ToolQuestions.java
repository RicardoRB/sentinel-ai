package dev.sentinel.infrastructure.cli.init;

import dev.sentinel.application.init.InitSetupCatalog;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.OpenSpecDistribution;
import dev.sentinel.domain.project.Project;
import java.util.List;

/** Resolves the optional OpenSpec tool, its validations, and installation consent. */
final class ToolQuestions {
  private static final String OPENSPEC = "openspec";

  private final InitQuestions.Answers answers;
  private final InitWizard wizard;
  private final InitConfirmations confirmations;
  private final boolean useDefaults;
  private final QuestionProgress progress;

  ToolQuestions(
      final InitQuestions.Answers answers,
      final InitWizard wizard,
      final InitConfirmations confirmations,
      final boolean useDefaults,
      final QuestionProgress progress) {
    this.answers = answers;
    this.progress = progress;
    this.wizard = wizard;
    this.confirmations = confirmations;
    this.useDefaults = useDefaults;
  }

  /** The selected tools, validations, and installs, validated against the catalog. */
  Selection resolve(final InitSetupCatalog catalog, final Project project) {
    final List<String> tools = tools(catalog);
    final Selection selection =
        new Selection(tools, validations(catalog, tools), installs(catalog, project, tools));
    selection.validate(catalog);
    return selection;
  }

  private List<String> tools(final InitSetupCatalog catalog) {
    if (answers.tools() != null) {
      return List.copyOf(answers.tools());
    }
    if (useDefaults) {
      return List.of();
    }
    progress.step();
    return wizard.selectTools(catalog);
  }

  private List<String> validations(final InitSetupCatalog catalog, final List<String> tools) {
    if (answers.validations() != null) {
      return List.copyOf(answers.validations());
    }
    if (useDefaults || !tools.contains(OPENSPEC)) {
      return List.of();
    }
    progress.expect(1);
    progress.step();
    return wizard.selectOpenSpecValidations(catalog);
  }

  private List<String> installs(
      final InitSetupCatalog catalog, final Project project, final List<String> tools) {
    if (answers.installTools() != null) {
      return List.copyOf(answers.installTools());
    }
    if (useDefaults || !tools.contains(OPENSPEC) || catalog.openSpecAvailable(project)) {
      return List.of();
    }
    progress.expect(1);
    progress.step();
    return confirmations.openSpecInstallation(OpenSpecDistribution.destination(project.root()))
        ? List.of(OPENSPEC)
        : List.of();
  }

  /** Tools chosen for init, with the validations and installs they enable. */
  record Selection(List<String> tools, List<String> validations, List<String> installs) {
    void validate(final InitSetupCatalog catalog) {
      if (!validations.isEmpty() && !tools.contains(OPENSPEC)) {
        throw new SentinelException("OpenSpec validations require --tool openspec.");
      }
      if (!installs.isEmpty() && !tools.contains(OPENSPEC)) {
        throw new SentinelException("--install-tool openspec requires --tool openspec.");
      }
      tools.forEach(
          tool ->
              catalog.tools().stream()
                  .filter(option -> option.id().equals(tool))
                  .findFirst()
                  .orElseThrow(() -> new SentinelException("Unknown tool '" + tool + "'.")));
      validations.forEach(
          validation -> {
            if (!catalog.openSpecValidations().contains(validation)) {
              throw new SentinelException("Unknown OpenSpec validation '" + validation + "'.");
            }
          });
    }
  }
}
