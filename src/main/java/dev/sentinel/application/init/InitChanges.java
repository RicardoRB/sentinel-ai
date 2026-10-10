package dev.sentinel.application.init;

import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.init.ArchitectureTestChange;
import dev.sentinel.domain.init.ArchitectureTestGeneration;
import dev.sentinel.domain.init.BuildToolConfiguration;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitResult;
import dev.sentinel.domain.init.InitSelection;
import dev.sentinel.domain.init.PomChange;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.init.RuleFileChange;
import dev.sentinel.domain.init.RuleFileGeneration;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Tracks one initialization transaction and rolls back its changes as a unit. */
final class InitChanges {
  private final Path root;
  private final List<String> integrationIds;
  private final ArchitectureTestGeneration architectureTests;
  private final RuleFileGeneration ruleFiles;
  private final BuildToolConfiguration pomTools;
  private final IntegrationInstaller installer;
  private final List<IntegrationResult> installed = new ArrayList<>();
  private PomChange pomChange;
  private RuleFileChange ruleFileChange;
  private ArchitectureTestChange architectureTest;

  InitChanges(
      final Path root,
      final List<String> integrationIds,
      final ArchitectureTestGeneration architectureTests,
      final RuleFileGeneration ruleFiles,
      final BuildToolConfiguration pomTools,
      final IntegrationInstaller installer) {
    this.root = root;
    this.integrationIds = integrationIds;
    this.architectureTests = architectureTests;
    this.ruleFiles = ruleFiles;
    this.pomTools = pomTools;
    this.installer = installer;
  }

  void apply(
      final Project project, final InitSelection selection, final List<InitGateOption> gates) {
    final QualityPreset preset = selection.preset();
    installer.install(project.root(), selection.integrations(), installed);
    pomChange = pomTools.apply(project, gates, preset);
    ruleFileChange = ruleFiles.apply(project, preset, gates);
    if (gates.stream().anyMatch(gate -> "archunit".equals(gate.id()))) {
      architectureTest =
          architectureTests.apply(
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
    return new InitResult(
        file,
        created,
        overwrite,
        gates,
        installed,
        pomChange.tools(),
        architectureTest,
        ruleFileChange == null ? List.of() : ruleFileChange.preserved(),
        pomChange.warnings());
  }

  void rollback() {
    architectureTests.rollback(architectureTest);
    ruleFiles.rollback(ruleFileChange);
    pomTools.rollback(pomChange);
    installer.rollbackNew(root, integrationIds, installed);
  }
}
