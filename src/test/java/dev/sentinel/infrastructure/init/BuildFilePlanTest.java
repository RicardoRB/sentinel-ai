package dev.sentinel.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.agent.PlannedFileChange;
import dev.sentinel.domain.agent.PlannedFileChange.Action;
import dev.sentinel.domain.init.CompatibilityProjectSnapshot;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.ToolchainSnapshot;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Build-file plans list each added or updated tool and never touch the file. */
class BuildFilePlanTest {
  private static final int JAVA_17 = 17;
  private static final String POM = "pom.xml";
  private static final String EMPTY_POM = "<project><modelVersion>4.0.0</modelVersion></project>";
  private static final String OLD_SPOTLESS =
      "<project><build><plugins><plugin><artifactId>spotless-maven-plugin</artifactId>"
          + "<version>1.0.0</version></plugin></plugins></build></project>";

  @Test
  void mavenPlanListsAddedPluginsWithVersionsAndWritesNothing(@TempDir final Path root)
      throws IOException {
    Files.writeString(root.resolve(POM), EMPTY_POM);

    final PlannedFileChange plan =
        new PomToolConfigurator().plan(maven(root), gates("pmd", "spotbugs"), null, false);

    assertThat(plan.file()).isEqualTo(root.resolve(POM));
    assertThat(plan.action()).isEqualTo(Action.MODIFY);
    assertThat(plan.details())
        .anyMatch(detail -> detail.matches("\\+ maven-pmd-plugin \\d+\\.\\d+\\.\\d+"))
        .anyMatch(detail -> detail.matches("\\+ spotbugs-maven-plugin \\d+[.\\d]*"));
    assertThat(Files.readString(root.resolve(POM))).isEqualTo(EMPTY_POM);
  }

  @Test
  void mavenPlanIsUnchangedWhenToolsAreDeclared(@TempDir final Path root) throws IOException {
    Files.writeString(root.resolve(POM), EMPTY_POM);
    final PomToolConfigurator configurator = new PomToolConfigurator();
    configurator.apply(maven(root), gates("pmd"));

    final PlannedFileChange plan = configurator.plan(maven(root), gates("pmd"), null, false);

    assertThat(plan.action()).isEqualTo(Action.UNCHANGED);
    assertThat(plan.details()).isEmpty();
  }

  @Test
  void pendingCompatibilityUpdateRequiresApproval(@TempDir final Path root) throws IOException {
    Files.writeString(root.resolve(POM), OLD_SPOTLESS);

    final PlannedFileChange plan = compatible().plan(maven(root), gates("format"), null, false);

    assertThat(plan.action()).isEqualTo(Action.MODIFY);
    assertThat(plan.details())
        .containsExactly("~ spotless-maven-plugin 1.0.0 -> 3.10.3 (requires approval)");
    assertThat(Files.readString(root.resolve(POM))).isEqualTo(OLD_SPOTLESS);
  }

  @Test
  void approvedCompatibilityUpdateIsListedAsAnUpdate(@TempDir final Path root) throws IOException {
    Files.writeString(root.resolve(POM), OLD_SPOTLESS);

    final PlannedFileChange plan = compatible().plan(maven(root), gates("format"), null, true);

    assertThat(plan.details()).containsExactly("~ spotless-maven-plugin 1.0.0 -> 3.10.3");
    assertThat(Files.readString(root.resolve(POM))).isEqualTo(OLD_SPOTLESS);
  }

  @Test
  void gradlePlanListsPluginsAndWritesNothing(@TempDir final Path root) throws IOException {
    final Path build = root.resolve("build.gradle");
    Files.writeString(build, "plugins { id 'java' }\n");
    final Project project = new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.NONE);

    final PlannedFileChange plan =
        new GradleToolConfigurator().plan(project, gates("archunit"), null, false);

    assertThat(plan.file()).isEqualTo(build);
    assertThat(plan.action()).isEqualTo(Action.MODIFY);
    assertThat(plan.details()).containsExactly("+ archunit-junit5 1.3.0");
    assertThat(Files.readString(build)).isEqualTo("plugins { id 'java' }\n");
  }

  private static PomToolConfigurator compatible() {
    return new PomToolConfigurator(
        project ->
            new CompatibilityProjectSnapshot(
                project,
                new ToolchainSnapshot(
                    JAVA_17, Optional.of("3.9.0"), Optional.empty(), Framework.NONE),
                Map.of("spotless-maven-plugin", "1.0.0")));
  }

  private static Project maven(final Path root) {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }

  private static List<InitGateOption> gates(final String... ids) {
    return List.of(ids).stream()
        .map(id -> new InitGateOption(id, id, List.of("mvn", id), true, "available"))
        .toList();
  }
}
