package dev.ward.infrastructure.cli.init;

import dev.ward.application.init.InitSetupCatalog;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.GateToolInstallCatalog;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.OpenSpecDistribution;
import dev.ward.domain.init.PlannedGateInstallation;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
  Selection resolve(
      final InitSetupCatalog catalog, final Project project, final List<String> gates) {
    final List<String> tools = tools(catalog);
    final Selection selection =
        new Selection(tools, validations(catalog, tools), installs(catalog, project, tools, gates));
    selection.validate(catalog, project, gates);
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
      final InitSetupCatalog catalog,
      final Project project,
      final List<String> tools,
      final List<String> gates) {
    if (answers.installTools() != null) {
      return List.copyOf(answers.installTools());
    }
    final List<String> selected = new ArrayList<>();
    if (!useDefaults && tools.contains(OPENSPEC) && !catalog.openSpecAvailable(project)) {
      openSpecInstall(project, selected);
    }
    if (!useDefaults) {
      gateInstalls(catalog, project, gates, selected);
    }
    return List.copyOf(selected);
  }

  private void openSpecInstall(final Project project, final List<String> selected) {
    progress.expect(1);
    progress.step();
    if (confirmations.openSpecInstallation(OpenSpecDistribution.destination(project.root()))) {
      selected.add(OPENSPEC);
    }
  }

  private void gateInstalls(
      final InitSetupCatalog catalog,
      final Project project,
      final List<String> gates,
      final List<String> selected) {
    for (final String gateId : gates) {
      final InitGateOption gate = catalog.gate(project, gateId);
      final Optional<PlannedGateInstallation> plan = catalog.gateInstallation(project, gate);
      if (plan.isPresent() && plan.get().installable()) {
        progress.expect(1);
        progress.step();
        final GateToolInstallCatalog.InstallMethod method = installMethod(plan.get());
        if (confirmations.gateToolInstallation(plan.get().tool(), method)) {
          selected.add(gateId);
        }
      }
    }
  }

  private static GateToolInstallCatalog.InstallMethod installMethod(
      final PlannedGateInstallation plan) {
    return GateToolInstallCatalog.forExecutable(plan.tool()).orElseThrow().methods().stream()
        .filter(candidate -> candidate.manager().equals(plan.manager()))
        .findFirst()
        .orElseThrow();
  }

  /** Tools chosen for init, with the validations and installs they enable. */
  record Selection(List<String> tools, List<String> validations, List<String> installs) {
    void validate(final InitSetupCatalog catalog, final Project project, final List<String> gates) {
      if (!validations.isEmpty() && !tools.contains(OPENSPEC)) {
        throw new WardException("OpenSpec validations require --tool openspec.");
      }
      tools.forEach(
          tool ->
              catalog.tools().stream()
                  .filter(option -> option.id().equals(tool))
                  .findFirst()
                  .orElseThrow(() -> new WardException("Unknown tool '" + tool + "'.")));
      validations.forEach(
          validation -> {
            if (!catalog.openSpecValidations().contains(validation)) {
              throw new WardException("Unknown OpenSpec validation '" + validation + "'.");
            }
          });
      installs.forEach(
          install -> {
            if (OPENSPEC.equals(install)) {
              if (!tools.contains(OPENSPEC)) {
                throw new WardException("--install-tool openspec requires --tool openspec.");
              }
              return;
            }
            if (!gates.contains(install)) {
              throw new WardException(
                  "--install-tool " + install + " requires --gate " + install + ".");
            }
            if (catalog.gate(project, install).available()
                || catalog.gateInstallation(project, catalog.gate(project, install)).isEmpty()
                || !catalog
                    .gateInstallation(project, catalog.gate(project, install))
                    .orElseThrow()
                    .installable()) {
              throw new WardException("Gate '" + install + "' is not installable.");
            }
          });
    }
  }
}
