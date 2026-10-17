package dev.sentinel.application.init;

import dev.sentinel.domain.agent.PlannedFileChange;
import dev.sentinel.domain.init.ArchitectureTestGeneration;
import dev.sentinel.domain.init.BuildToolConfiguration;
import dev.sentinel.domain.init.ConfigurationStorage;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitPlan;
import dev.sentinel.domain.init.InitSelection;
import dev.sentinel.domain.init.OpenSpecDistribution;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.init.RuleFileGeneration;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Builds the plan of everything initialization would change, without changing anything. */
final class InitPlanner {
  private final Supplier<InitSetupCatalog> catalog;
  private final IntegrationInstaller installer;
  private final BuildToolConfiguration buildTools;
  private final RuleFileGeneration ruleFiles;
  private final ArchitectureTestGeneration architectureTests;
  private final Supplier<ConfigurationStorage> storage;

  InitPlanner(
      final Supplier<InitSetupCatalog> catalog,
      final IntegrationInstaller installer,
      final BuildToolConfiguration buildTools,
      final RuleFileGeneration ruleFiles,
      final ArchitectureTestGeneration architectureTests,
      final Supplier<ConfigurationStorage> storage) {
    this.catalog = catalog;
    this.installer = installer;
    this.buildTools = buildTools;
    this.ruleFiles = ruleFiles;
    this.architectureTests = architectureTests;
    this.storage = storage;
  }

  /** Lists the changes in the order {@code InitChanges.apply} makes them. */
  InitPlan plan(final Project project, final InitSelection selection, final boolean approved) {
    installer.validate(selection.integrations());
    final boolean installs = !selection.installTools().isEmpty();
    final String executable =
        installs ? OpenSpecDistribution.executable(Path.of("")).toString() : "openspec";
    final List<InitGateOption> gates =
        SelectedGates.resolve(catalog.get(), project, selection, executable);
    final List<PlannedFileChange> files = new ArrayList<>();
    files.add(configuration(project.root().resolve("sentinel.toml"), gates, selection.preset()));
    files.addAll(installer.plan(project.root(), selection.integrations()));
    if (selection.qualityHooks()) {
      files.addAll(catalog.get().qualityHooks().plan(project.root()));
    }
    files.add(buildTools.plan(project, gates, selection.preset(), approved));
    files.addAll(ruleFiles.plan(project, selection.preset(), gates));
    if (selection.gates().contains("archunit")) {
      files.add(
          architectureTests.plan(
              project,
              selection.architecture() == null ? "layered" : selection.architecture(),
              selection.preset() == null
                  ? new QualityPreset.ArchitectureExtras(false, false, false)
                  : selection.preset().rules().architectureExtras()));
    }
    return new InitPlan(
        files,
        installs
            ? Optional.of(OpenSpecDistribution.installation(project.root()))
            : Optional.empty());
  }

  private PlannedFileChange configuration(
      final Path file, final List<InitGateOption> gates, final QualityPreset preset) {
    final Optional<String> existing = storage.get().read(file);
    if (existing.isEmpty()) {
      return new PlannedFileChange(file, PlannedFileChange.Action.CREATE);
    }
    return new PlannedFileChange(
        file,
        ConfigurationTemplate.render(gates, preset).equals(existing.get())
            ? PlannedFileChange.Action.UNCHANGED
            : PlannedFileChange.Action.MODIFY);
  }
}
