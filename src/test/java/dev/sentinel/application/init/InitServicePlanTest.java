package dev.sentinel.application.init;

import static dev.sentinel.ProjectFixtures.PLAIN_POM;
import static dev.sentinel.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.CompatibilityFixtures;
import dev.sentinel.ProjectSnapshot;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.FakeEnvironmentInspection;
import dev.sentinel.domain.agent.PlannedFileChange;
import dev.sentinel.domain.agent.PlannedFileChange.Action;
import dev.sentinel.domain.init.InitPlan;
import dev.sentinel.domain.init.InitSelection;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.infrastructure.agent.ClaudeCodeIntegration;
import dev.sentinel.infrastructure.init.ArchitectureTestGenerator;
import dev.sentinel.infrastructure.init.FileConfigurationStorage;
import dev.sentinel.infrastructure.init.PomToolConfigurator;
import dev.sentinel.infrastructure.init.PresetRuleFileWriter;
import dev.sentinel.infrastructure.init.QualityHookInstaller;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitServicePlanTest {

  @Test
  void standardClaudeAndHooksListEveryFileAndWriteNothing(@TempDir final Path dir)
      throws IOException {
    withPom(dir, PLAIN_POM);
    final Map<String, String> before = ProjectSnapshot.of(dir);

    final InitPlan plan =
        service(new PomToolConfigurator())
            .plan(dir, selection(List.of("claude-code"), List.of(), true), false);

    assertThat(actions(dir, plan))
        .containsEntry("sentinel.toml", Action.CREATE)
        .containsEntry(".claude/settings.json", Action.CREATE)
        .containsEntry(".claude/hooks/sentinel-edit-write", Action.CREATE)
        .containsEntry(".git/hooks/pre-commit", Action.CREATE)
        .containsEntry(".git/hooks/pre-push", Action.CREATE)
        .containsEntry("pom.xml", Action.MODIFY)
        .containsEntry("config/pmd-ruleset.xml", Action.CREATE)
        .containsEntry("config/checkstyle.xml", Action.CREATE)
        .containsEntry("config/spotbugs-exclude.xml", Action.CREATE);
    assertThat(plan.hasConflict()).isFalse();
    assertThat(plan.installation()).isEmpty();
    assertThat(ProjectSnapshot.of(dir)).isEqualTo(before);
  }

  @Test
  void userOwnedIntegrationTargetIsAConflict(@TempDir final Path dir) throws IOException {
    withPom(dir, PLAIN_POM);
    Files.createDirectories(dir.resolve(".claude"));
    Files.writeString(dir.resolve(".claude/settings.json"), "{ not json");

    final InitPlan plan =
        service(new PomToolConfigurator())
            .plan(dir, selection(List.of("claude-code"), List.of(), false), false);

    assertThat(plan.hasConflict()).isTrue();
  }

  @Test
  void compatibilityUpdateIsPendingUntilApproved(@TempDir final Path dir) throws IOException {
    Files.writeString(dir.resolve("pom.xml"), CompatibilityFixtures.OUTDATED_SPOTLESS_POM);
    final InitService service = service(CompatibilityFixtures.outdatedSpotless());
    final InitSelection format =
        new InitSelection(
            List.of("none"), List.of("format"), null, null, List.of(), List.of(), List.of(), false);

    assertThat(pom(dir, service.plan(dir, format, false)).details())
        .containsExactly("~ spotless-maven-plugin 1.0.0 -> 3.10.3 (requires approval)");
    assertThat(pom(dir, service.plan(dir, format, true)).details())
        .containsExactly("~ spotless-maven-plugin 1.0.0 -> 3.10.3");
  }

  @Test
  void consentedInstallationIsDescribed(@TempDir final Path dir) throws IOException {
    withPom(dir, PLAIN_POM);

    final InitPlan plan =
        service(new PomToolConfigurator())
            .plan(dir, selection(List.of("none"), List.of("openspec"), false), false);

    assertThat(plan.installation())
        .hasValueSatisfying(
            installation -> {
              assertThat(installation.packageName()).startsWith("@fission-ai/openspec@");
              assertThat(installation.destination())
                  .isEqualTo(dir.resolve(".sentinel/tools/openspec"));
            });
  }

  private static InitSelection selection(
      final List<String> integrations, final List<String> install, final boolean hooks) {
    return new InitSelection(
        integrations,
        QualityPreset.STANDARD.gates(),
        null,
        QualityPreset.STANDARD,
        install.isEmpty() ? List.of() : List.of("openspec"),
        List.of(),
        install,
        hooks);
  }

  private static InitService service(final PomToolConfigurator pom) {
    return new InitService(
        new ProjectDetector(new FileSystemProjectInspection()),
        Set.of(new ClaudeCodeIntegration()),
        new InitSetupCatalog(new FakeEnvironmentInspection(), new QualityHookInstaller()),
        pom,
        new ArchitectureTestGenerator(),
        new FileConfigurationStorage(),
        new PresetRuleFileWriter());
  }

  private static PlannedFileChange pom(final Path dir, final InitPlan plan) {
    return plan.files().stream()
        .filter(change -> change.file().equals(dir.resolve("pom.xml")))
        .findFirst()
        .orElseThrow();
  }

  private static Map<String, Action> actions(final Path dir, final InitPlan plan) {
    return plan.files().stream()
        .collect(
            Collectors.toMap(
                change -> dir.relativize(change.file()).toString(), PlannedFileChange::action));
  }
}
