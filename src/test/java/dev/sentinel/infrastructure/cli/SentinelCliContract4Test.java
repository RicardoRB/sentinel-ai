package dev.sentinel.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.WINDOWS)
class SentinelCliContract4Test extends SentinelCliIntegrationSupport {
  @Test
  void initInstallsMultipleIntegrationsSelectedWithSpaces() {
    assertThat(runInitWithInput("3\n2 3\n1\n", "-C", project.toString())).isZero();
    assertThat(project.resolve(".opencode/commands/sentinel-check.md")).exists();
    assertThat(project.resolve(".opencode/plugins/sentinel-edit-write.js")).exists();
    assertThat(project.resolve(".claude/settings.json")).exists();
    assertThat(out.toString())
        .contains("Created Sentinel-owned OpenCode", "Created Sentinel-owned Claude Code");
  }

  @Test
  void initConfiguresMultipleQualityGatesSelectedWithSpaces() throws IOException {
    assertThat(runInitWithInput("3\n1\n1 2\n", "-C", project.toString())).isZero();
    final String config = Files.readString(project.resolve(SENTINEL_TOML));
    assertThat(config).contains("[quality-gates.tests]", "[quality-gates.compile]");
  }

  @Test
  void archunitSelectionPromptsForArchitectureAndGeneratesTest() throws IOException {
    assertThat(runInitWithInput("3\n1\n9\n2\n", "-C", project.toString())).isZero();
    final Path test = project.resolve("src/test/java/com/example/ArchitectureTest.java");
    assertThat(test).exists();
    assertThat(Files.readString(test))
        .contains("hexagonal", "@AnalyzeClasses(packages = \"com.example\"");
    assertThat(Files.readString(project.resolve("pom.xml"))).contains("archunit-junit5");
    assertThat(out.toString())
        .contains("Select an architecture style", "Generated hexagonal ArchUnit test");
  }

  @Test
  void archunitGateTargetsOnlyGeneratedArchitectureTest() throws IOException {
    assertThat(
            run(
                INIT,
                INTEGRATION_OPTION,
                NONE,
                GATE_OPTION,
                "archunit",
                "--architecture",
                "layered",
                "-C",
                project.toString()))
        .isZero();
    assertThat(Files.readString(project.resolve(SENTINEL_TOML)))
        .contains("./mvnw -Dtest=ArchitectureTest test");
  }

  @Test
  void existingArchitectureTestIsPreserved() throws IOException {
    final Path test = project.resolve("src/test/java/com/example/ArchitectureTest.java");
    Files.createDirectories(test.getParent());
    Files.writeString(test, "user-owned architecture test\n");

    assertThat(runInitWithInput("3\n1\n9\n1\n", "-C", project.toString())).isZero();
    assertThat(test).hasContent("user-owned architecture test\n");
    assertThat(out.toString()).contains("Preserved existing ArchUnit test");
  }

  @Test
  void selectedCheckstyleAddsMissingMavenPluginAndReportsIt() throws IOException {
    assertThat(
            run(
                INIT,
                INTEGRATION_OPTION,
                NONE,
                GATE_OPTION,
                "checkstyle",
                "-C",
                project.toString()))
        .isZero();
    assertThat(Files.readString(project.resolve("pom.xml"))).contains("maven-checkstyle-plugin");
    assertThat(out.toString())
        .contains("Updated pom.xml with Maven tools", "maven-checkstyle-plugin");
  }

  @Test
  void existingMavenPluginIsNotDuplicated() throws IOException {
    final Path pom = project.resolve("pom.xml");
    final String original = Files.readString(pom);
    Files.writeString(
        pom,
        original.replace(
            "</project>",
            """
                <build><plugins><plugin><artifactId>maven-checkstyle-plugin</artifactId></plugin></plugins></build>
                </project>"""));

    assertThat(
            run(
                INIT,
                INTEGRATION_OPTION,
                NONE,
                GATE_OPTION,
                "checkstyle",
                "-C",
                project.toString()))
        .isZero();
    final String updated = Files.readString(pom);
    assertThat(updated.indexOf("<artifactId>maven-checkstyle-plugin</artifactId>"))
        .isEqualTo(updated.lastIndexOf("<artifactId>maven-checkstyle-plugin</artifactId>"));
    assertThat(out.toString()).doesNotContain("Updated pom.xml with Maven tools");
  }

  @Test
  void generatedMavenAndArchitectureFilesRollBackOnLaterSetupFailure() throws IOException {
    final Path testRoot = project.resolve("src/test/java");
    Files.createDirectories(testRoot.getParent());
    Files.writeString(testRoot, "not a directory\n");
    final String originalPom = Files.readString(project.resolve("pom.xml"));

    assertThat(
            run(
                INIT,
                INTEGRATION_OPTION,
                NONE,
                GATE_OPTION,
                "archunit",
                "--architecture",
                "clean",
                "-C",
                project.toString()))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(Files.readString(project.resolve("pom.xml"))).isEqualTo(originalPom);
    assertThat(project.resolve(SENTINEL_TOML)).doesNotExist();
  }
}
