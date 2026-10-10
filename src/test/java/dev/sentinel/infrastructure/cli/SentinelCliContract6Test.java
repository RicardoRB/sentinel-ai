package dev.sentinel.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.JsonTree;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.WINDOWS)
class SentinelCliContract6Test extends SentinelCliIntegrationSupport {
  @Test
  void checkRejectsZapWithoutTargetAsConfigurationError() throws IOException {
    Files.writeString(
        project.resolve(SENTINEL_TOML),
        """
                version = 1
                [quality-gates.zap]
                command = ["zap-baseline.py", "-t", "<TARGET_URL>"]
                """);

    final int exit = run(CHECK, FORMAT_OPTION, JSON, "-C", project.toString());

    assertThat(exit).isEqualTo(ExitCodes.ERROR);
    assertThat(JsonTree.parse(out.toString()).get(STATUS).asString()).isEqualTo("ERROR");
    assertThat(err.toString()).contains("requires an explicit target");
  }

  @Test
  void doctorReportsConfigurationAndIntegrationStates() throws IOException {
    assertThat(run("doctor", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(out.toString()).contains("configuration", "ERROR", "integration", "WARNING");
    out.getBuffer().setLength(0);
    run(INIT, INTEGRATION_OPTION, "opencode", GATE_OPTION, TESTS, "-C", project.toString());
    assertThat(run("doctor", "-C", project.toString())).isZero();
    assertThat(out.toString()).contains("configuration", "OK", "integration", "OK");
  }

  @Test
  void directIntegrationHandlesNotFoundAndRemoval() {
    assertThat(run("integrate", "opencode", "--remove", "-C", project.toString())).isZero();
    assertThat(out.toString()).contains("NOT_FOUND");
    out.getBuffer().setLength(0);
    assertThat(run("integrate", CLAUDE_CODE, "--remove", "-C", project.toString())).isZero();
    assertThat(out.toString()).contains("NOT_FOUND");
  }

  @Test
  void interactiveIntegrationRejectsInvalidAndEof() {
    assertThat(runWithInput("9\n", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("Invalid agent selection");
    err.getBuffer().setLength(0);
    assertThat(runWithInput("", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("No agent selected");
  }

  @Test
  void checkWithoutLearnAfterCreatesNoSentinelDirectory() {
    run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, TESTS, "-C", project.toString());
    out.getBuffer().setLength(0);

    assertThat(run(CHECK, "-C", project.toString())).isEqualTo(ExitCodes.OK);
    assertThat(project.resolve(".sentinel")).doesNotExist();
  }

  @Test
  void learnAfterBelowOneIsAUsageError() {
    run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, TESTS, "-C", project.toString());
    out.getBuffer().setLength(0);

    assertThat(run(CHECK, LEARN_AFTER, "0", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("--learn-after must be at least 1");
    assertThat(project.resolve(".sentinel")).doesNotExist();
  }

  @Test
  void learningPromptEmittedAfterFailPassFailPassInJson() throws IOException {
    run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, TESTS, "-C", project.toString());

    fail();
    out.getBuffer().setLength(0);
    assertThat(run(CHECK, FORMAT_OPTION, JSON, LEARN_AFTER, "2", "-C", project.toString()))
        .isEqualTo(ExitCodes.FAILED);

    pass();
    out.getBuffer().setLength(0);
    assertThat(run(CHECK, FORMAT_OPTION, JSON, LEARN_AFTER, "2", "-C", project.toString()))
        .isEqualTo(ExitCodes.OK);
    assertThat(JsonTree.parse(out.toString()).get("learning").get("prompts").size()).isZero();

    fail();
    out.getBuffer().setLength(0);
    assertThat(run(CHECK, FORMAT_OPTION, JSON, LEARN_AFTER, "2", "-C", project.toString()))
        .isEqualTo(ExitCodes.FAILED);

    pass();
    out.getBuffer().setLength(0);
    assertThat(run(CHECK, FORMAT_OPTION, JSON, LEARN_AFTER, "2", "-C", project.toString()))
        .isEqualTo(ExitCodes.OK);
    final JsonTree root = JsonTree.parse(out.toString());
    assertThat(root.get(STATUS).asString()).isEqualTo("PASSED");
    final JsonTree prompt = root.get("learning").get("prompts");
    assertThat(prompt.size()).isEqualTo(1);
    assertThat(prompt.get(0).get("gate").asString()).isEqualTo(TESTS);
    assertThat(prompt.get(0).get("occurrences").asInt()).isEqualTo(2);
    assertThat(prompt.get(0).get("instruction").asString()).contains("AGENTS.md");

    final JsonTree ledger =
        JsonTree.parse(Files.readString(project.resolve(".sentinel/learning.json")));
    assertThat(ledger.get("formatVersion").asInt()).isEqualTo(1);
    assertThat(ledger.get("records").get("tests:FAILED").get("occurrences").asInt()).isEqualTo(2);
    assertThat(ledger.get("records").get("tests:FAILED").get("prompted").asBoolean()).isTrue();
    assertThat(out.toString()).doesNotContain("✓", "\u001b");
  }

  @Test
  void learningTextReportShowsSectionAndLeavesAgentsMdUntouched() throws IOException {
    run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, TESTS, "-C", project.toString());
    final Path agents = project.resolve("AGENTS.md");
    Files.writeString(agents, "project contract\n");

    fail();
    assertThat(run(CHECK, LEARN_AFTER, "2", "-C", project.toString())).isEqualTo(ExitCodes.FAILED);
    pass();
    assertThat(run(CHECK, LEARN_AFTER, "2", "-C", project.toString())).isEqualTo(ExitCodes.OK);
    fail();
    assertThat(run(CHECK, LEARN_AFTER, "2", "-C", project.toString())).isEqualTo(ExitCodes.FAILED);
    out.getBuffer().setLength(0);
    pass();
    assertThat(run(CHECK, LEARN_AFTER, "2", "-C", project.toString())).isEqualTo(ExitCodes.OK);

    final String text = out.toString();
    assertThat(text)
        .contains("Learning", "tests (2 occurrences):", "AGENTS.md", "Quality Gate: PASSED");
    assertThat(text.indexOf("Learning")).isGreaterThan(text.indexOf("Quality Gate"));
    assertThat(agents).hasContent("project contract\n");
  }
}
