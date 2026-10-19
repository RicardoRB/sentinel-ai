package dev.sentinel.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.JsonTree;
import dev.sentinel.infrastructure.cli.ExitCodes;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClaudeCodeHookStopTest extends ClaudeCodeHookTestSupport {
  @Test
  void stopBlocksOnceWithCombinedQualityAndSpecFindings() {
    specsValid = false;

    assertThat(hook(stop(false))).isEqualTo(ExitCodes.OK);

    final JsonTree output = JsonTree.parse(out.toString());
    assertThat(output.get("decision").asString()).isEqualTo("block");
    assertThat(output.get("reason").asString())
        .contains("openspec (FAILED): 1 error", "must include at least one scenario");
    assertThat(commands)
        .containsExactly(
            List.of("mvn", "test"),
            List.of("openspec", "validate", "--all", "--strict", "--no-interactive"));
    assertThat(err.toString()).isEmpty();
  }

  @Test
  void stopAlreadyContinuedReportsToTheUserWithoutBlocking() {
    specsValid = false;

    assertThat(hook(stop(true))).isEqualTo(ExitCodes.OK);

    final JsonTree output = JsonTree.parse(out.toString());
    assertThat(output.get("systemMessage").asString()).contains("openspec (FAILED)");
    assertThat(out.toString()).doesNotContain("decision");
  }

  @Test
  void cleanStopPrintsNothing() {
    assertThat(hook(stop(false))).isEqualTo(ExitCodes.OK);
    assertThat(out.toString()).isEmpty();
    assertThat(err.toString()).isEmpty();
  }

  @Test
  void stopWithoutOpenSpecIsSilent() throws IOException {
    Files.writeString(
        root.resolve("sentinel.toml"),
        "version = 1\n[quality-gates.tests]\ncommand = \"mvn test\"\n");

    assertThat(hook(stop(false))).isEqualTo(ExitCodes.OK);
    assertThat(out.toString()).isEmpty();
    assertThat(commands).containsExactly(List.of("mvn", "test"));
  }

  @Test
  void stopSentinelErrorIsShownToTheUserAndNeverBlocks() throws IOException {
    Files.writeString(root.resolve("sentinel.toml"), "version = [");

    assertThat(hook(stop(false))).isEqualTo(ExitCodes.OK);

    final JsonTree output = JsonTree.parse(out.toString());
    assertThat(output.get("systemMessage").asString()).startsWith("sentinel: ");
    assertThat(out.toString()).doesNotContain("block");
  }
}
