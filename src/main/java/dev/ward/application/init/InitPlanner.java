package dev.ward.application.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.InitPlan;
import dev.ward.domain.init.InitSelection;
import dev.ward.domain.init.OpenSpecDistribution;
import dev.ward.domain.init.PlannedGateInstallation;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Builds the plan of everything initialization would change, without changing anything. */
final class InitPlanner {
  private final Supplier<InitSetupCatalog> catalog;
  private final IntegrationInstaller installer;
  private final InitProjectFiles files;

  InitPlanner(
      final Supplier<InitSetupCatalog> catalog,
      final IntegrationInstaller installer,
      final InitProjectFiles files) {
    this.catalog = catalog;
    this.installer = installer;
    this.files = files;
  }

  /** Lists the changes in the order {@code InitChanges.apply} makes them. */
  InitPlan plan(final Project project, final InitSelection selection, final boolean approved) {
    installer.validate(selection.integrations());
    final boolean installs = selection.installTools().contains(OpenSpecInstallation.TOOL);
    final String executable =
        installs ? OpenSpecDistribution.executable(Path.of("")).toString() : "openspec";
    final List<String> existingArchUnitTests =
        files.architectureTests().existingArchUnitTests(project);
    final List<InitGateOption> gates =
        ArchUnitGateReuse.rewrite(
            project,
            SelectedGates.resolve(catalog.get(), project, selection, executable),
            existingArchUnitTests);
    final List<PlannedGateInstallation> gateInstallations = gateInstallations(project, gates);
    final List<PlannedFileChange> changes = new ArrayList<>();
    changes.add(
        configuration(project, project.root().resolve("ward.toml"), gates, selection.preset()));
    changes.addAll(installer.plan(project.root(), selection.integrations()));
    if (selection.qualityHooks()) {
      changes.addAll(catalog.get().qualityHooks().plan(project.root()));
    }
    changes.add(files.buildTools().plan(project, gates, selection.preset(), approved));
    final List<PlannedFileChange> ruleFiles =
        files.ruleFiles().plan(project, selection.preset(), gates);
    changes.addAll(ruleFiles);
    changes.addAll(architectureFiles(project, selection, existingArchUnitTests));
    final List<Path> userOwned =
        ruleFiles.stream()
            .filter(file -> file.action() == PlannedFileChange.Action.PRESERVE_USER_OWNED)
            .map(PlannedFileChange::file)
            .toList();
    OwnedSections.of(catalog.get(), project, selection, gates, userOwned).stream()
        .map(files.sections()::plan)
        .forEach(changes::add);
    return new InitPlan(
        changes,
        installs
            ? Optional.of(OpenSpecDistribution.installation(project.root()))
            : Optional.empty(),
        gateInstallations);
  }

  private List<PlannedGateInstallation> gateInstallations(
      final Project project, final List<InitGateOption> gates) {
    return gates.stream()
        .map(gate -> catalog.get().gateInstallation(project, gate))
        .flatMap(Optional::stream)
        .toList();
  }

  private List<PlannedFileChange> architectureFiles(
      final Project project, final InitSelection selection, final List<String> existing) {
    if (!selection.gates().contains("archunit")) {
      return List.of();
    }
    if (!existing.isEmpty()) {
      return List.of(
          new PlannedFileChange(
              project.root().resolve("src/test/java"),
              PlannedFileChange.Action.UNCHANGED,
              List.of("Reusing existing ArchUnit tests: " + String.join(", ", existing))));
    }
    return List.of(
        files
            .architectureTests()
            .plan(
                project,
                selection.architecture() == null ? "layered" : selection.architecture(),
                selection.preset() == null
                    ? new QualityPreset.ArchitectureExtras(false, false, false)
                    : selection.preset().rules().architectureExtras()));
  }

  private PlannedFileChange configuration(
      final Project project,
      final Path file,
      final List<InitGateOption> gates,
      final QualityPreset preset) {
    final Optional<String> existing = files.configuration().read(file);
    if (existing.isEmpty()) {
      return new PlannedFileChange(file, PlannedFileChange.Action.CREATE);
    }
    return new PlannedFileChange(
        file,
        ConfigurationTemplate.render(gates, preset, project).equals(existing.get())
            ? PlannedFileChange.Action.UNCHANGED
            : PlannedFileChange.Action.MODIFY);
  }
}
