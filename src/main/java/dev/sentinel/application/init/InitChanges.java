package dev.sentinel.application.init;

import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.ArchitectureTestChange;
import dev.sentinel.domain.init.CompatibilityFinding;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitResult;
import dev.sentinel.domain.init.InitSelection;
import dev.sentinel.domain.init.PomChange;
import dev.sentinel.domain.init.QualityHookChange;
import dev.sentinel.domain.init.QualityHookGeneration;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.init.RuleFileChange;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Tracks one initialization transaction and rolls back its changes as a unit. */
final class InitChanges {
  private final Path root;
  private final List<String> integrationIds;
  private final InitProjectFiles files;
  private final IntegrationInstaller installer;
  private final QualityHookGeneration qualityHooks;
  private final List<IntegrationResult> installed = new ArrayList<>();
  private PomChange pomChange;
  private RuleFileChange ruleFileChange;
  private ArchitectureTestChange architectureTest;
  private QualityHookChange qualityHookChange;

  InitChanges(
      final Path root,
      final List<String> integrationIds,
      final InitProjectFiles files,
      final IntegrationInstaller installer,
      final QualityHookGeneration qualityHooks) {
    this.root = root;
    this.integrationIds = integrationIds;
    this.files = files;
    this.installer = installer;
    this.qualityHooks = qualityHooks;
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
        throw new SentinelException(qualityHookChange.result().message());
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
  }

  InitResult result(
      final Path file,
      final boolean created,
      final boolean overwrite,
      final List<InitGateOption> gates) {
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
        appliedPom.warnings());
  }

  void rollback() {
    files.architectureTests().rollback(architectureTest);
    files.ruleFiles().rollback(ruleFileChange);
    files.buildTools().rollback(pomChange);
    qualityHooks.rollback(qualityHookChange);
    installer.rollbackNew(root, integrationIds, installed);
  }
}
