package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@DisabledOnOs(OS.WINDOWS)
class WardCliContract2Test extends WardCliIntegrationSupport {
  @Test
  void checkJsonWritesOnlyJsonToStdout() {
    assertThat(run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, "format", "-C", project.toString()))
        .isZero();
    out.getBuffer().setLength(0);

    assertThat(run(CHECK, FORMAT_OPTION, JSON, "-C", project.toString())).isZero();
    assertThat(JsonTree.parse(out.toString()).get(STATUS).asString()).isEqualTo("PASSED");
    assertThat(out.toString()).doesNotContain("Ward", "✓", "…", "\\u001b");
  }

  @Test
  void loopCommandUsesProductionCompositionAndRuntimeRequestOptions() {
    assertThat(run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, "format", "-C", project.toString()))
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
    Files.writeString(project.resolve(WARD_TOML), "version = 1\n");

    assertThat(run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, "format", "-C", project.toString()))
        .isZero();

    assertThat(out.toString()).contains("Kept existing ward.toml. No changes were made.");
    assertThat(project.resolve(WARD_TOML)).hasContent("version = 1\n");
  }

  @Test
  void initAsksBeforeOverwritingExistingConfiguration() throws IOException {
    Files.writeString(
        project.resolve(WARD_TOML),
        "version = 1\n\n[quality-gates.compile]\nenabled = true\ncommand = \"old\"\n");

    assertThat(runInitWithInput("n\n", "-C", project.toString())).isZero();
    assertThat(Files.readString(project.resolve(WARD_TOML))).contains("command = \"old\"");

    out.getBuffer().setLength(0);
    assertThat(runInitWithInput("y\n3\n1\n1\n", "-C", project.toString())).isZero();
    assertThat(Files.readString(project.resolve(WARD_TOML))).contains("[quality-gates.tests]");
    assertThat(out.toString()).contains("Overwrite it?", "Updated ");
  }

  @Test
  void initThenFailingCheckExitsWithOne() throws IOException {
    run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, "format", "-C", project.toString());
    Files.createFile(project.resolve("FAIL"));
    out.getBuffer().setLength(0);

    assertThat(run(CHECK, "-C", project.toString())).isEqualTo(ExitCodes.FAILED);
    assertThat(out.toString())
        .contains(
            "✗ format",
            "FAILED",
            "Quality Gate: FAILED",
            "Command:",
            "./mvnw spotless:check",
            "BUILD FAILURE");
  }

  @Test
  void jsonOutputIsValidJsonAndNothingElse() throws IOException {
    run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, "format", "-C", project.toString());
    Files.createFile(project.resolve("FAIL"));
    out.getBuffer().setLength(0);

    final int exit = run(CHECK, FORMAT_OPTION, JSON, "-C", project.toString());

    assertThat(exit).isEqualTo(ExitCodes.FAILED);
    final JsonTree json = JsonTree.parse(out.toString()); // fails on any extra text
    assertThat(json.get(STATUS).asString()).isEqualTo("FAILED");
    assertThat(json.get(CHECKS).get(0).get("exitCode").asInt()).isEqualTo(1);
    assertThat(json.get(CHECKS).get(0).get("output").asString())
        .contains("stub mvnw: spotless:check");
    assertThat(json.get(CHECKS).get(0).has("stdout")).isFalse();
    assertThat(json.get(CHECKS).get(0).has("stderr")).isFalse();
  }

  @Test
  void checkWithoutConfigurationIsAnErrorWithCleanJson() {
    final int exit = run(CHECK, FORMAT_OPTION, JSON, "-C", project.toString());

    assertThat(exit).isEqualTo(ExitCodes.ERROR);
    assertThat(JsonTree.parse(out.toString()).get(STATUS).asString()).isEqualTo("ERROR");
    assertThat(err.toString()).contains("ward init");
  }

  @Test
  void checkOutsideAProjectIsAnError(@TempDir final Path empty) {
    assertThat(run(CHECK, "-C", empty.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("No supported project detected.");
  }
}
