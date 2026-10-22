package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Generated Dependency-Check blocks keep OSS Index off and read credentials from the environment.
 */
class GradleDependencyCheckConfigurationTest {
  @TempDir Path root;

  @Test
  void groovyDisablesOssIndexWithEnvironmentCredentials() throws IOException {
    assertThat(configure("build.gradle", "plugins {\n    id 'java'\n}\n"))
        .contains(
            "    analyzers {\n        ossIndex {\n            enabled = false\n",
            "username = System.getenv('OSS_INDEX_USERNAME')",
            "password = System.getenv('OSS_INDEX_PASSWORD')");
  }

  @Test
  void kotlinDisablesOssIndexWithEnvironmentCredentials() throws IOException {
    assertThat(configure("build.gradle.kts", "plugins {\n    java\n}\n"))
        .contains(
            "    analyzers {\n        ossIndex {\n            enabled = false\n",
            "username = System.getenv(\"OSS_INDEX_USERNAME\")",
            "password = System.getenv(\"OSS_INDEX_PASSWORD\")");
  }

  private String configure(final String file, final String script) throws IOException {
    Files.writeString(root.resolve(file), script);
    new GradleToolConfigurator()
        .apply(
            new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.NONE),
            List.of(
                new InitGateOption(
                    "dependency-check",
                    "dependency-check",
                    List.of("./gradlew", "dependencyCheckAnalyze"),
                    true,
                    "available")),
            null,
            finding -> false);
    return Files.readString(root.resolve(file));
  }
}
