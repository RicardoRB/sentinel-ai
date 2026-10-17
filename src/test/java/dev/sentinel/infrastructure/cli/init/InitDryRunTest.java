package dev.sentinel.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.CompatibilityFixtures;
import dev.sentinel.ProjectSnapshot;
import dev.sentinel.infrastructure.cli.ExitCodes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** --dry-run previews without writing, and compatibility approval shows in the plan. */
class InitDryRunTest {

  @TempDir Path root;

  @Test
  void dryRunPrintsThePlanAndChangesNothing() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);
    final Map<String, String> before = ProjectSnapshot.of(root);

    final int exit =
        init.run(
            "",
            "--dry-run",
            "--preset",
            "strict",
            "--integration",
            "none",
            "--architecture",
            "layered");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output())
        .contains(
            "Sentinel will make these changes:", "create     sentinel.toml", "modify     pom.xml");
    assertThat(ProjectSnapshot.of(root)).isEqualTo(before);
  }

  @Test
  void dryRunListsTheInstallationWithoutInstalling() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    final int exit =
        init.run(
            "",
            "--dry-run",
            "--gate",
            "tests",
            "--integration",
            "none",
            "--tool",
            "openspec",
            "--install-tool",
            "openspec");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output()).contains("install    openspec @fission-ai/openspec@");
    assertThat(root.resolve(".sentinel")).doesNotExist();
  }

  @Test
  void acceptCompatibilityUpdatesAppliesWithoutPromptingButStillAsks() throws IOException {
    Files.writeString(root.resolve("pom.xml"), CompatibilityFixtures.OUTDATED_SPOTLESS_POM);
    final InitCommandFixture init =
        new InitCommandFixture(root, CompatibilityFixtures.outdatedSpotless());

    final int exit =
        init.run(
            true,
            "\nn\n\n",
            "--accept-compatibility-updates",
            "--integration",
            "none",
            "--gate",
            "format");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output())
        .contains("Select optional tools", "~ spotless-maven-plugin 1.0.0 -> 3.10.3")
        .doesNotContain("Compatibility update proposed", "(requires approval)");
    assertThat(Files.readString(root.resolve("pom.xml"))).contains("<version>3.10.3</version>");
  }

  @Test
  void compatibilityUpdateIsPendingWithoutApproval() throws IOException {
    Files.writeString(root.resolve("pom.xml"), CompatibilityFixtures.OUTDATED_SPOTLESS_POM);
    final InitCommandFixture init =
        new InitCommandFixture(root, CompatibilityFixtures.outdatedSpotless());

    final int exit = init.run("", "--dry-run", "--integration", "none", "--gate", "format");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output())
        .contains("~ spotless-maven-plugin 1.0.0 -> 3.10.3 (requires approval)");
    assertThat(Files.readString(root.resolve("pom.xml")))
        .isEqualTo(CompatibilityFixtures.OUTDATED_SPOTLESS_POM);
  }
}
