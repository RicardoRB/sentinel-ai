package dev.ward.application.init;

import dev.ward.domain.agent.IntegrationResult;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.ArchitectureTestChange;
import dev.ward.domain.init.CompatibilityFinding;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitResult;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.OwnedSection;
import dev.ward.domain.init.OwnedSectionChange;
import dev.ward.domain.init.PomChange;
import dev.ward.domain.init.QualityHookChange;
import dev.ward.domain.init.QualityHookGeneration;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.RuleFileChange;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** Tracks one initialization transaction and rolls back its changes as a unit. */
final class InitChanges {
  private final Path root;
  private final List<String> integrationIds;
  private final InitProjectFiles files;
  private final IntegrationInstaller installer;
  private final InitSetupCatalog catalog;
  private final QualityHookGeneration qualityHooks;
  private final List<IntegrationResult> installed = new ArrayList<>();
  private final List<OwnedSectionChange> sectionChanges = new ArrayList<>();
  private PomChange pomChange;
  private RuleFileChange ruleFileChange;
  private ArchitectureTestChange architectureTest;
  private QualityHookChange qualityHookChange;

  InitChanges(
      final Path root,
      final List<String> integrationIds,
      final InitProjectFiles files,
      final IntegrationInstaller installer,
      final InitSetupCatalog catalog) {
    this.root = root;
    this.integrationIds = integrationIds;
    this.files = files;
    this.installer = installer;
    this.catalog = catalog;
    this.qualityHooks = catalog.qualityHooks();
  }

  void apply(
      final Project project,
      final InitSelection selection,
      final List<InitGateOption> gates,
      final Predicate<? super CompatibilityFinding> approval) {
    final QualityPreset preset = selection.preset();
    installer.install(project.root(), selection.integrations(), installed);
    if (selection.qualityHooks()) {
      qualityHookChange = qualityHooks.apply(project.root());
      if (qualityHookChange.result().status() == IntegrationResult.Status.CONFLICT) {
        throw new WardException(qualityHookChange.result().message());
      }
    }
    pomChange = files.buildTools().apply(project, gates, preset, approval);
    ruleFileChange = files.ruleFiles().apply(project, preset, gates);
    if (gates.stream().anyMatch(gate -> "archunit".equals(gate.id()))) {
      architectureTest =
          files
              .architectureTests()
              .apply(
                  project,
                  selection.architecture() == null ? "layered" : selection.architecture(),
                  preset == null
                      ? new QualityPreset.ArchitectureExtras(false, false, false)
                      : preset.rules().architectureExtras());
    }
    for (final OwnedSection section :
        OwnedSections.of(catalog, project, selection, gates, ruleFileChange.preserved())) {
      sectionChanges.add(files.sections().apply(section));
    }
  }

  InitResult result(
      final Path file,
      final boolean created,
      final boolean overwrite,
      final List<InitGateOption> gates,
      final List<String> warnings) {
    final PomChange appliedPom = pomChange;
    if (appliedPom == null) {
      throw new IllegalStateException("Initialization changes have not been applied");
    }
    return new InitResult(
        file,
        created,
        overwrite,
        gates,
        installed,
        appliedPom.tools(),
        architectureTest,
        ruleFileChange == null ? List.of() : ruleFileChange.preserved(),
        Stream.concat(appliedPom.warnings().stream(), warnings.stream()).toList(),
        sectionChanges.stream()
            .filter(OwnedSectionChange::changed)
            .map(OwnedSectionChange::file)
            .toList());
  }

  void rollback() {
    sectionChanges.reversed().forEach(files.sections()::rollback);
    files.architectureTests().rollback(architectureTest);
    files.ruleFiles().rollback(ruleFileChange);
    files.buildTools().rollback(pomChange);
    qualityHooks.rollback(qualityHookChange);
    installer.rollbackNew(root, integrationIds, installed);
  }
}
