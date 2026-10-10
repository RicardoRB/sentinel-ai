package dev.sentinel.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.JsonTree;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@DisabledOnOs(OS.WINDOWS)
class SentinelCliContract2Test extends SentinelCliIntegrationSupport {
  @Test
  void checkJsonWritesOnlyJsonToStdout() {
    assertThat(run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, TESTS, "-C", project.toString()))
        .isZero();
    out.getBuffer().setLength(0);

    assertThat(run(CHECK, FORMAT_OPTION, JSON, "-C", project.toString())).isZero();
    assertThat(JsonTree.parse(out.toString()).get(STATUS).asString()).isEqualTo("PASSED");
    assertThat(out.toString()).doesNotContain("Sentinel", "✓", "…", "\\u001b");
  }

  @Test
  void loopCommandUsesProductionCompositionAndRuntimeRequestOptions() {
    assertThat(run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, TESTS, "-C", project.toString()))
        .isZero();
    out.getBuffer().setLength(0);

    final int exit =
        run(
            "loop",
            "complete this task",
            "--agent-command",
            "echo",
            "--max-iterations",
            "1",
            "--timeout-seconds",
            "5",
            "--allow-dirty",
            "-C",
            project.toString());

    assertThat(exit).isZero();
    assertThat(out.toString()).contains("PASSED after 1 iteration(s)");
  }

  @Test
  void initDoesNotOverwriteAndExitsCleanly() throws IOException {
    Files.writeString(project.resolve(SENTINEL_TOML), "version = 1\n");

    assertThat(run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, TESTS, "-C", project.toString()))
        .isZero();

    assertThat(out.toString()).contains("already exists");
    assertThat(project.resolve(SENTINEL_TOML)).hasContent("version = 1\n");
  }

  @Test
  void initAsksBeforeOverwritingExistingConfiguration() throws IOException {
    Files.writeString(
        project.resolve(SENTINEL_TOML),
        "version = 1\n\n[quality-gates.compile]\nenabled = true\ncommand = \"old\"\n");

    assertThat(runInitWithInput("n\n", "-C", project.toString())).isZero();
    assertThat(Files.readString(project.resolve(SENTINEL_TOML))).contains("command = \"old\"");

    out.getBuffer().setLength(0);
    assertThat(runInitWithInput("y\n3\n1\n1\n", "-C", project.toString())).isZero();
    assertThat(Files.readString(project.resolve(SENTINEL_TOML))).contains("[quality-gates.tests]");
    assertThat(out.toString()).contains("Overwrite it?", "Updated ");
  }

  @Test
  void initThenFailingCheckExitsWithOne() throws IOException {
    run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, TESTS, "-C", project.toString());
    Files.createFile(project.resolve("FAIL"));
    out.getBuffer().setLength(0);

    assertThat(run(CHECK, "-C", project.toString())).isEqualTo(ExitCodes.FAILED);
    assertThat(out.toString())
        .contains(
            "✗ tests",
            "FAILED",
            "Quality Gate: FAILED",
            "Command:",
            "./mvnw test",
            "BUILD FAILURE");
  }

  @Test
  void jsonOutputIsValidJsonAndNothingElse() throws IOException {
    run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, TESTS, "-C", project.toString());
    Files.createFile(project.resolve("FAIL"));
    out.getBuffer().setLength(0);

    final int exit = run(CHECK, FORMAT_OPTION, JSON, "-C", project.toString());

    assertThat(exit).isEqualTo(ExitCodes.FAILED);
    final JsonTree json = JsonTree.parse(out.toString()); // fails on any extra text
    assertThat(json.get(STATUS).asString()).isEqualTo("FAILED");
    assertThat(json.get(CHECKS).get(0).get("exitCode").asInt()).isEqualTo(1);
    assertThat(json.get(CHECKS).get(0).get("stdout").asString()).contains("stub mvnw: test");
    assertThat(json.get(CHECKS).get(0).get("stderr").asString()).contains("BUILD FAILURE");
  }

  @Test
  void checkWithoutConfigurationIsAnErrorWithCleanJson() {
    final int exit = run(CHECK, FORMAT_OPTION, JSON, "-C", project.toString());

    assertThat(exit).isEqualTo(ExitCodes.ERROR);
    assertThat(JsonTree.parse(out.toString()).get(STATUS).asString()).isEqualTo("ERROR");
    assertThat(err.toString()).contains("sentinel init");
  }

  @Test
  void checkOutsideAProjectIsAnError(@TempDir final Path empty) {
    assertThat(run(CHECK, "-C", empty.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("No supported project detected.");
  }
}
