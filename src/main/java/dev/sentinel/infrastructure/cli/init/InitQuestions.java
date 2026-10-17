package dev.sentinel.infrastructure.cli.init;

import dev.sentinel.application.init.InitSetupCatalog;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.InitSelection;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.util.List;

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

  private final Answers answers;
  private final InitWizard wizard;
  private final InitConfirmations confirmations;
  private final InitSetupCatalog catalog;
  private final Project project;
  private final boolean useDefaults;

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
      final boolean terminal) {
    this.answers = answers;
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

  InitSelection resolve() {
    final boolean gatesGiven =
        answers.preset() != null || answers.gates() != null && !answers.gates().isEmpty();
    final QualityPreset preset = preset(gatesGiven);
    final List<String> integrations = integrations();
    final List<String> gates = gates(gatesGiven, preset);
    // A preset given as an option or by default keeps its architecture default; only chosen gates
    // prompt for a style.
    final List<String> chosenGates =
        gatesGiven ? orEmpty(answers.gates()) : useDefaults ? List.of() : gates;
    final String architecture = architecture(chosenGates, gates);
    final ToolQuestions.Selection tools =
        new ToolQuestions(answers, wizard, confirmations, useDefaults).resolve(catalog, project);
    final boolean qualityHooks =
        answers.qualityHooks() != null
            ? answers.qualityHooks()
            : !useDefaults && confirmations.qualityHooks();
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
    return wizard.selectPreset();
  }

  private List<String> integrations() {
    if (answers.integrations() != null && !answers.integrations().isEmpty()) {
      return answers.integrations();
    }
    return useDefaults
        ? List.of(InitSetupCatalog.NO_INTEGRATION)
        : wizard.selectIntegrations(catalog);
  }

  private List<String> gates(final boolean gatesGiven, final QualityPreset preset) {
    if (gatesGiven) {
      final List<String> explicit = orEmpty(answers.gates());
      return preset == null ? explicit : preset.withExplicitGates(explicit);
    }
    return answers.yes() ? preset.gates() : wizard.selectGates(catalog, project, preset);
  }

  private String architecture(final List<String> chosenGates, final List<String> gates) {
    if (!chosenGates.contains(ARCHUNIT)) {
      return answers.architecture() == null && useDefaults && gates.contains(ARCHUNIT)
          ? DEFAULT_ARCHITECTURE
          : answers.architecture();
    }
    final String selected =
        answers.architecture() != null
            ? answers.architecture()
            : useDefaults ? DEFAULT_ARCHITECTURE : wizard.selectArchitecture(catalog);
    catalog.architecture(selected);
    return selected;
  }

  private static List<String> orEmpty(final List<String> values) {
    return values == null ? List.of() : values;
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
