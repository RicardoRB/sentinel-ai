package dev.sentinel.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.WINDOWS)
class SentinelCliContract3Test extends SentinelCliIntegrationSupport {
  @Test
  void invalidFormatIsAUsageError() {
    assertThat(run(CHECK, FORMAT_OPTION, "xml")).isEqualTo(ExitCodes.ERROR);
  }

  @Test
  void integratesClaudeCodeAndOpenCodeAndRemovesOwnedArtifacts() {
    assertThat(run("integrate", CLAUDE_CODE, "-C", project.toString())).isZero();
    assertThat(project.resolve(".claude/settings.json")).exists();
    assertThat(project.resolve(".claude/hooks/sentinel-edit-write")).exists();

    out.getBuffer().setLength(0);
    assertThat(run("integrate", "opencode", "-C", project.toString())).isZero();
    assertThat(project.resolve(".opencode/commands/sentinel-check.md")).exists();
    assertThat(project.resolve(".opencode/plugins/sentinel-edit-write.js")).exists();

    assertThat(run("integrate", CLAUDE_CODE, "--remove", "-C", project.toString())).isZero();
    assertThat(project.resolve(".claude/settings.json")).doesNotExist();
    assertThat(run("integrate", "opencode", "--remove", "-C", project.toString())).isZero();
    assertThat(project.resolve(".opencode/plugins/sentinel-edit-write.js")).doesNotExist();
  }

  @Test
  void unknownIntegrationListsSupportedAgentsWithoutWritingFiles() {
    assertThat(run("integrate", "unknown", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("Supported agents: opencode, claude-code");
  }

  @Test
  void promptsForAgentWhenArgumentIsOmitted() {
    assertThat(runWithInput("2\n", "-C", project.toString())).isZero();
    assertThat(out.toString())
        .contains("Select an agent integration", "1) opencode", "2) claude-code");
    assertThat(project.resolve(".claude/settings.json")).exists();
  }

  @Test
  void rejectsInvalidInteractiveSelectionWithoutWritingFiles() {
    assertThat(runWithInput("9\n", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("Invalid agent selection");
    assertThat(project.resolve(".claude")).doesNotExist();
    assertThat(project.resolve(".opencode")).doesNotExist();
  }

  @Test
  void rejectsEndOfInputWithoutWritingFiles() {
    assertThat(runWithInput("", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("No agent selected");
    assertThat(project.resolve(".claude")).doesNotExist();
    assertThat(project.resolve(".opencode")).doesNotExist();
  }

  @Test
  void initPromptsForIntegrationAndGateAndCreatesSelectedConfiguration() throws IOException {
    assertThat(runInitWithInput("3\n1\n1\n", "-C", project.toString())).isZero();
    assertThat(out.toString())
        .contains("Select agent integrations", "> [ ] 1)", "Select quality gates", "AVAILABLE");
    assertThat(Files.readString(project.resolve(SENTINEL_TOML))).contains("[quality-gates.tests]");
  }

  @Test
  void initFallsBackToNumberedPromptsWhenRawTerminalIsUnavailable() throws IOException {
    assertThat(runInitWithInput("1\n3\n1\n", "-C", project.toString())).isZero();

    assertThat(out.toString())
        .contains(
            "enter numbers separated by spaces", "Toggle integrations", "Toggle quality gates");
    assertThat(project.resolve(SENTINEL_TOML)).exists();
  }
}
