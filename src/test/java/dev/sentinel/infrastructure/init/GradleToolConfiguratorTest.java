package dev.sentinel.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.PomChange;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GradleToolConfiguratorTest {
  @Test
  void configuresGroovyBuildAndRollsBack(@TempDir final Path root) throws Exception {
    final Path build = root.resolve("build.gradle");
    Files.writeString(build, "plugins { id 'java' }\n\n// user configuration\n");
    final Project project = new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.NONE);
    final GradleToolConfigurator configurator = new GradleToolConfigurator();

    final PomChange change =
        configurator.apply(project, List.of(gate("format")), null, new Object());

    final String updated = Files.readString(build);
    assertThat(updated).contains("com.diffplug.spotless", "user configuration");
    assertThat(change.file()).isEqualTo(build);
    configurator.rollback(change);
    assertThat(Files.readString(build)).isEqualTo(change.originalContent());
  }

  @Test
  void configuresKotlinDslAndDoesNotDuplicateExistingPlugin(@TempDir final Path root)
      throws Exception {
    final Path build = root.resolve("build.gradle.kts");
    final String original = "plugins { id(\"com.diffplug.spotless\") version \"6.25.0\" }\n";
    Files.writeString(build, original);
    final Project project = new Project(root, Language.KOTLIN, BuildTool.GRADLE, Framework.NONE);

    final PomChange change =
        new GradleToolConfigurator().apply(project, List.of(gate("format")), null, new Object());

    assertThat(change.changed()).isFalse();
    assertThat(Files.readString(build)).isEqualTo(original);
  }

  @Test
  void usesPinnedGradlePluginDeclarationsForAllManagedGates(@TempDir final Path root)
      throws Exception {
    final Path build = root.resolve("build.gradle");
    Files.writeString(build, "plugins { id 'java' }\n");
    final Project project = new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.NONE);
    final List<String> ids =
        List.of(
            "format",
            "spotbugs",
            "coverage",
            "dependency-check",
            "mutation",
            "sonar",
            "license",
            "api-compat");

    new GradleToolConfigurator()
        .apply(
            project,
            ids.stream().map(GradleToolConfiguratorTest::gate).toList(),
            null,
            new Object());

    assertThat(Files.readString(build))
        .contains(
            "com.diffplug.spotless",
            "com.github.spotbugs",
            "org.owasp.dependencycheck",
            "info.solidsoft.pitest",
            "org.sonarqube",
            "com.github.hierynomus.license",
            "me.champeau.japicmp");
  }

  private static InitGateOption gate(final String id) {
    return new InitGateOption(id, id, List.of("./gradlew", id), true, "available");
  }
}
