package dev.ward.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import dev.ward.infrastructure.cli.ExitCodes;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A partial check after each edit, and the full check when the turn ends. */
class ClaudeCodeHookPartialCheckTest extends ClaudeCodeHookTestSupport {
  private static final List<String> TESTS = List.of("mvn", "test");
  private static final List<String> CHECKSTYLE = List.of("mvn", "checkstyle:check");

  @BeforeEach
  void partialConfiguration() throws IOException {
    Files.writeString(
        root.resolve("ward.toml"),
        CONFIG
            + """
            [quality-gates.checkstyle]
            command = "mvn checkstyle:check"
            [quality-gates.checkstyle.partial]
            inputs = ["src/**/*.java"]
            args = ["-Dcheckstyle.includes={paths}"]
            item = "**/{name}"
            separator = ","
            """);
    Files.createDirectories(root.resolve("src/main/java"));
    Files.writeString(root.resolve("src/main/java/App.java"), "class App {}\n");
  }

  @Test
  void markdownEditSkipsTheGateThatDoesNotReadIt() {
    assertThat(hook(postToolUse(root.resolve("README.md").toString()))).isEqualTo(ExitCodes.OK);

    assertThat(commands).containsExactly(TESTS);
  }

  @Test
  void javaEditNarrowsTheGateToTheEditedFile() {
    assertThat(hook(postToolUse(root.resolve("src/main/java/App.java").toString())))
        .isEqualTo(ExitCodes.OK);

    assertThat(commands)
        .containsExactly(
            TESTS, List.of("mvn", "checkstyle:check", "-Dcheckstyle.includes=**/App.java"));
  }

  @Test
  void relativeEditedPathIsResolvedAgainstTheProject() {
    hook(postToolUse("src/main/java/App.java"));

    assertThat(commands.get(1)).contains("-Dcheckstyle.includes=**/App.java");
  }

  @Test
  void deletedJavaFileRunsTheGateInFull() {
    hook(postToolUse(root.resolve("src/main/java/Gone.java").toString()));

    assertThat(commands).containsExactly(TESTS, CHECKSTYLE);
  }

  @Test
  void eventWithoutPathRunsEveryGateInFull() {
    hook(
        """
        {"hook_event_name":"PostToolUse","tool_name":"Edit","tool_input":{}}
        """);

    assertThat(commands).containsExactly(TESTS, CHECKSTYLE);
  }

  @Test
  void stopRunsTheFullCheckAndBlocksOnAQualityFailure() {
    testsPass = false;

    assertThat(hook(stop(false))).isEqualTo(ExitCodes.OK);

    assertThat(commands).startsWith(TESTS, CHECKSTYLE);
    assertThat(JsonTree.parse(out.toString()).get("decision").asString()).isEqualTo("block");
    assertThat(JsonTree.parse(out.toString()).get("reason").asString()).contains("tests: ");
  }

  @Test
  void stopAlreadyContinuingReportsQualityFailuresWithoutBlocking() {
    testsPass = false;

    assertThat(hook(stop(true))).isEqualTo(ExitCodes.OK);

    final JsonTree output = JsonTree.parse(out.toString());
    assertThat(output.has("decision")).isFalse();
    assertThat(output.get("systemMessage").asString()).contains("tests: ");
  }
}
