package dev.sentinel.infrastructure.cli.init;

import dev.sentinel.application.init.InitSetupCatalog;
import dev.sentinel.application.init.PreviousChoices;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.InitSelection;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves every init question the same way: the option that answers it, otherwise the default when
 * {@code --yes} is given, otherwise an answer read by the wizard.
 */
final class InitQuestions {
  static final String DEFAULT_ARCHITECTURE = "layered";
  static final String GATES_MISSING =
      "No gate selection given. In non-interactive mode pass --preset or --gate, or --yes to"
          + " accept defaults.";
  private static final String ARCHUNIT = "archunit";
  private static final int STANDARD_CHOICE = 1;
  private static final int STRICT_CHOICE = 2;
  private static final int CUSTOM_CHOICE = 3;

  private final Answers answers;
  private final InitWizard wizard;
  private final InitConfirmations confirmations;
  private final InitSetupCatalog catalog;
  private final Project project;
  private final boolean useDefaults;
  private final QuestionProgress progress;
  private Optional<PreviousChoices> previous = Optional.empty();

  /**
   * Answers are read only on a terminal, or when no setup option was given (piped answers).
   * Automation that passes setup options is never asked, so an open but silent input cannot block.
   */
  InitQuestions(
      final Answers answers,
      final InitWizard wizard,
      final InitConfirmations confirmations,
      final InitSetupCatalog catalog,
      final Project project,
      final boolean terminal,
      final QuestionProgress progress) {
    this.answers = answers;
    this.progress = progress;
    this.wizard = wizard;
    this.confirmations = confirmations;
    this.catalog = catalog;
    this.project = project;
    this.useDefaults = answers.yes() || !terminal && answers.setupGiven();
  }

  /** Whether an existing configuration may be overwritten after asking. */
  boolean confirmOverwrite(final Path file) {
    return !useDefaults && confirmations.overwrite(file);
  }

  /** Pre-fills the preset default and gate selection from the configuration being replaced. */
  void reconfigure(final PreviousChoices choices) {
    previous = Optional.of(choices);
  }

  InitSelection resolve() {
    final boolean gatesGiven =
        answers.preset() != null || answers.gates() != null && !answers.gates().isEmpty();
    progress.expect(questionsToAsk(gatesGiven));
    final QualityPreset preset = preset(gatesGiven);
    final List<String> integrations = integrations();
    final List<String> gates = gates(gatesGiven, preset);
    // A preset given as an option or by default keeps its architecture default; only chosen gates
    // prompt for a style.
    final List<String> chosenGates =
        gatesGiven
            ? Objects.requireNonNullElse(answers.gates(), List.<String>of())
            : useDefaults ? List.of() : gates;
    final String architecture = architecture(chosenGates, gates);
    final ToolQuestions.Selection tools =
        new ToolQuestions(answers, wizard, confirmations, useDefaults, progress)
            .resolve(catalog, project, gates);
    final boolean qualityHooks =
        answers.qualityHooks() != null ? answers.qualityHooks() : askQualityHooks();
    return new InitSelection(
        integrations,
        gates,
        architecture,
        preset,
        tools.tools(),
        tools.validations(),
        tools.installs(),
        qualityHooks);
  }

  private QualityPreset preset(final boolean gatesGiven) {
    if (gatesGiven) {
      return answers.preset() == null ? null : catalog.preset(answers.preset());
    }
    if (answers.yes()) {
      return QualityPreset.STANDARD;
    }
    if (useDefaults) {
      throw new SentinelException(GATES_MISSING);
    }
    progress.step();
    return wizard.selectPreset(previous.map(InitQuestions::presetChoice).orElse(STANDARD_CHOICE));
  }

  private List<String> integrations() {
    if (answers.integrations() != null && !answers.integrations().isEmpty()) {
      return answers.integrations();
    }
    if (useDefaults) {
      return List.of(InitSetupCatalog.NO_INTEGRATION);
    }
    progress.step();
    return wizard.selectIntegrations(catalog);
  }

  private List<String> gates(final boolean gatesGiven, final QualityPreset preset) {
    if (gatesGiven) {
      final List<String> explicit = Objects.requireNonNullElse(answers.gates(), List.<String>of());
      return preset == null ? explicit : preset.withExplicitGates(explicit);
    }
    if (answers.yes()) {
      return preset.gates();
    }
    progress.step();
    return wizard.selectGates(
        catalog,
        project,
        previous
            .filter(choices -> choices.preset() == preset)
            .map(PreviousChoices::gates)
            .orElse(preset == null ? List.of() : preset.gates()));
  }

  private String architecture(final List<String> chosenGates, final List<String> gates) {
    if (!chosenGates.contains(ARCHUNIT)) {
      return answers.architecture() == null && useDefaults && gates.contains(ARCHUNIT)
          ? DEFAULT_ARCHITECTURE
          : answers.architecture();
    }
    final String selected;
    if (answers.architecture() != null) {
      selected = answers.architecture();
    } else if (useDefaults) {
      selected = DEFAULT_ARCHITECTURE;
    } else {
      progress.expect(1);
      progress.step();
      selected = wizard.selectArchitecture(catalog);
    }
    catalog.architecture(selected);
    return selected;
  }

  private boolean askQualityHooks() {
    if (useDefaults) {
      return false;
    }
    progress.step();
    return confirmations.qualityHooks();
  }

  /** The base questions this run will ask; follow-ups are added when they become certain. */
  private int questionsToAsk(final boolean gatesGiven) {
    if (useDefaults) {
      return 0;
    }
    int questions = gatesGiven ? 0 : 2;
    if (answers.integrations() == null || answers.integrations().isEmpty()) {
      questions++;
    }
    if (answers.tools() == null) {
      questions++;
    }
    if (answers.qualityHooks() == null) {
      questions++;
    }
    return questions;
  }

  /** The preset question's number for a recorded preset; no preset means custom. */
  private static int presetChoice(final PreviousChoices choices) {
    if (choices.preset() == null) {
      return CUSTOM_CHOICE;
    }
    return choices.preset() == QualityPreset.STRICT ? STRICT_CHOICE : STANDARD_CHOICE;
  }

  /** The command-line answers; {@code null} means the option was not given. */
  record Answers(
      String preset,
      List<String> integrations,
      List<String> gates,
      String architecture,
      List<String> tools,
      List<String> validations,
      List<String> installTools,
      Boolean qualityHooks,
      boolean yes) {
    boolean setupGiven() {
      return preset != null || gates != null || integrations != null;
    }
  }
}
