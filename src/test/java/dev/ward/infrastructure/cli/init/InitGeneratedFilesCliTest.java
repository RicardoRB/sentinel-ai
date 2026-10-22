package dev.ward.infrastructure.cli.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.cli.ExitCodes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code --ci} and {@code --agent-rules} preview, write, and reject their files like other files.
 */
class InitGeneratedFilesCliTest {
  private static final String WORKFLOW = ".github/workflows/ward.yml";
  @TempDir Path root;

  @Test
  void dryRunListsTheWorkflowAndAgentRulesWithoutWritingThem() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    final int exit =
        init.run(
            "",
            "--dry-run",
            "--integration",
            "none",
            "--preset",
            "strict",
            "--ci",
            "github",
            "--agent-rules");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(init.output())
        .contains("create     " + WORKFLOW, "create     AGENTS.md", "Ward section");
    assertThat(root.resolve(WORKFLOW)).doesNotExist();
    assertThat(root.resolve("AGENTS.md")).doesNotExist();
  }

  @Test
  void applyWritesThePinnedWorkflowAndKeepsUserAgentInstructions() throws IOException {
    Files.writeString(root.resolve("AGENTS.md"), "# Team rules\n\nUse records.\n");
    final InitCommandFixture init = new InitCommandFixture(root);

    final int exit =
        init.run(
            "", "--integration", "none", "--preset", "strict", "--ci", "github", "--agent-rules");

    assertThat(exit).as(init.output()).isEqualTo(ExitCodes.OK);
    assertThat(Files.readString(root.resolve(WORKFLOW)))
        .contains("WARD_VERSION: v1.2.3", "ward check --profile ci --format sarif");
    assertThat(Files.readString(root.resolve("AGENTS.md")))
        .startsWith("# Team rules\n\nUse records.\n\n<!-- ward-owned: agent-rules v1 begin")
        .contains("files of at most 500 lines", "line at least 85%");
    assertThat(init.output()).contains("Wrote Ward section: ");
  }

  @Test
  void userOwnedWorkflowBlocksInitializationWithoutChanges() throws IOException {
    Files.createDirectories(root.resolve(".github/workflows"));
    Files.writeString(root.resolve(WORKFLOW), "name: mine\n");
    final InitCommandFixture init = new InitCommandFixture(root);

    final int exit =
        init.run("", "--integration", "none", "--preset", "standard", "--ci", "github");

    assertThat(exit).isEqualTo(ExitCodes.ERROR);
    assertThat(init.output()).contains("is user-owned");
    assertThat(Files.readString(root.resolve(WORKFLOW))).isEqualTo("name: mine\n");
    assertThat(root.resolve("ward.toml")).doesNotExist();
  }

  @Test
  void unknownCiProviderIsRejected() throws IOException {
    final InitCommandFixture init = new InitCommandFixture(root);

    final int exit =
        init.run("", "--integration", "none", "--preset", "standard", "--ci", "gitlab");

    assertThat(exit).isEqualTo(ExitCodes.ERROR);
    assertThat(init.output()).contains("Unknown CI provider 'gitlab'. Supported providers: github");
  }
}
