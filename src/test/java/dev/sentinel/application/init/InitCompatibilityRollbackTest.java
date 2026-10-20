package dev.sentinel.application.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.FakeEnvironmentInspection;
import dev.sentinel.domain.agent.PlannedFileChange;
import dev.sentinel.domain.init.CompatibilityProjectSnapshot;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.InitSelection;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.init.RuleFileChange;
import dev.sentinel.domain.init.RuleFileGeneration;
import dev.sentinel.domain.init.ToolchainSnapshot;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import dev.sentinel.infrastructure.init.ArchitectureTestGenerator;
import dev.sentinel.infrastructure.init.FileConfigurationStorage;
import dev.sentinel.infrastructure.init.PomToolConfigurator;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitCompatibilityRollbackTest {
  private static final int JAVA_17 = 17;

  @Test
  void restoresCompatibilityUpdateWhenLaterInitializationStepFails(@TempDir final Path root)
      throws Exception {
    final String original =
        "<project><build><plugins><plugin><artifactId>spotless-maven-plugin</artifactId>"
            + "<version>1.0.0</version></plugin></plugins></build></project>";
    Files.writeString(root.resolve("pom.xml"), original);
    final PomToolConfigurator configurator =
        new PomToolConfigurator(
            ignored ->
                new CompatibilityProjectSnapshot(
                    new Project(Path.of("."), Language.JAVA, BuildTool.MAVEN, Framework.NONE),
                    new ToolchainSnapshot(
                        JAVA_17, Optional.of("3.9.0"), Optional.empty(), Framework.NONE),
                    Map.of("spotless-maven-plugin", "1.0.0")));
    final RuleFileGeneration failingRules =
        new RuleFileGeneration() {
          @Override
          public RuleFileChange apply(
              final Project project, final QualityPreset preset, final List<InitGateOption> gates) {
            throw new IllegalStateException("later setup failed");
          }

          @Override
          public List<PlannedFileChange> plan(
              final Project project, final QualityPreset preset, final List<InitGateOption> gates) {
            return List.of();
          }

          @Override
          public void rollback(final RuleFileChange change) {}
        };
    final InitService service =
        new InitService(
            new ProjectDetector(new FileSystemProjectInspection()),
            Set.of(),
            new InitSetupCatalog(new FakeEnvironmentInspection()),
            new InitProjectFiles(
                configurator,
                failingRules,
                new ArchitectureTestGenerator(),
                new FileConfigurationStorage()));

    assertThatThrownBy(
            () ->
                service.initialize(
                    root,
                    new InitSelection(List.of("none"), List.of("format")),
                    false,
                    finding -> true))
        .hasMessage("later setup failed");
    assertThat(Files.readString(root.resolve("pom.xml"))).isEqualTo(original);
  }
}
