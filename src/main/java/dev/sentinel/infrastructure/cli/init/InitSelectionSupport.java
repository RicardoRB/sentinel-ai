package dev.sentinel.infrastructure.cli.init;

import dev.sentinel.application.init.InitSetupCatalog;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.project.Project;
import java.util.List;

/** Pure selection helpers kept separate from Picocli command plumbing. */
final class InitSelectionSupport {
  private InitSelectionSupport() {}

  static List<String> gatesOption(final List<String> gates) {
    return gates == null ? List.of() : gates;
  }

  static QualityPreset preset(
      final InitWizard wizard, final QualityPreset requested, final boolean explicit) {
    return explicit ? requested : wizard.selectPreset();
  }

  static List<String> integrations(
      final InitWizard wizard, final InitSetupCatalog catalog, final List<String> integrations) {
    return integrations == null || integrations.isEmpty()
        ? wizard.selectIntegrations(catalog)
        : integrations;
  }

  static List<String> gates(
      final InitWizard wizard,
      final InitSetupCatalog catalog,
      final Project project,
      final List<String> options,
      final QualityPreset preset,
      final boolean explicit) {
    return explicit ? options : wizard.selectGates(catalog, project, preset);
  }

  static List<String> tools(
      final InitWizard wizard,
      final InitSetupCatalog catalog,
      final List<String> requested,
      final boolean provided,
      final boolean explicit) {
    if (provided) {
      return List.copyOf(requested);
    }
    return explicit ? List.of() : wizard.selectTools(catalog);
  }

  static List<String> validations(
      final InitWizard wizard,
      final InitSetupCatalog catalog,
      final List<String> requested,
      final boolean explicit,
      final List<String> selectedTools) {
    if (requested != null) {
      return List.copyOf(requested);
    }
    return !explicit && selectedTools.contains("openspec")
        ? wizard.selectOpenSpecValidations(catalog)
        : List.of();
  }

  static List<String> installTools(
      final InitWizard wizard,
      final InitSetupCatalog catalog,
      final Project project,
      final List<String> requested,
      final boolean explicit,
      final List<String> selectedTools) {
    if (requested != null) {
      return List.copyOf(requested);
    }
    if (!explicit
        && selectedTools.contains("openspec")
        && !catalog.openSpecAvailable(project)
        && wizard.confirmOpenSpecInstallation(project.root().resolve(".sentinel/tools/openspec"))) {
      return List.of("openspec");
    }
    return List.of();
  }

  static void validate(
      final InitSetupCatalog catalog,
      final List<String> selectedTools,
      final List<String> selectedValidations,
      final List<String> selectedInstallTools) {
    if (!selectedValidations.isEmpty() && !selectedTools.contains("openspec")) {
      throw new SentinelException("OpenSpec validations require --tool openspec.");
    }
    if (!selectedInstallTools.isEmpty() && !selectedTools.contains("openspec")) {
      throw new SentinelException("--install-tool openspec requires --tool openspec.");
    }
    selectedTools.forEach(
        tool ->
            catalog.tools().stream()
                .filter(option -> option.id().equals(tool))
                .findFirst()
                .orElseThrow(() -> new SentinelException("Unknown tool '" + tool + "'.")));
    selectedValidations.forEach(
        validation -> {
          if (!catalog.openSpecValidations().contains(validation)) {
            throw new SentinelException("Unknown OpenSpec validation '" + validation + "'.");
          }
        });
  }
}
