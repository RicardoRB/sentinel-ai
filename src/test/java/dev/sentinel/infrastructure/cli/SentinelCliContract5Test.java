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
class SentinelCliContract5Test extends SentinelCliIntegrationSupport {
  @Test
  void rejectsCombiningNoIntegrationWithAnAgent() {
    assertThat(runInitWithInput("3\n1 2\n1\n", "-C", project.toString()))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("no-integration choice cannot be combined");
    assertThat(project.resolve(SENTINEL_TOML)).doesNotExist();
    assertThat(project.resolve(".opencode")).doesNotExist();
  }

  @Test
  void rollsBackEarlierIntegrationWhenLaterSelectionConflicts() throws IOException {
    final Path target = project.resolve(".opencode/commands/sentinel-check.md");
    Files.createDirectories(target.getParent());
    Files.writeString(target, "user-owned\n");

    assertThat(runInitWithInput("3\n3 2\n1\n", "-C", project.toString()))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(project.resolve(".claude")).doesNotExist();
    assertThat(project.resolve(SENTINEL_TOML)).doesNotExist();
    assertThat(target).hasContent("user-owned\n");
  }

  @Test
  void initReportsIntegrationConflictWithoutCreatingConfiguration() throws IOException {
    final Path target = project.resolve(".opencode/commands/sentinel-check.md");
    Files.createDirectories(target.getParent());
    Files.writeString(target, "user-owned\n");

    assertThat(
            run(INIT, INTEGRATION_OPTION, "opencode", GATE_OPTION, TESTS, "-C", project.toString()))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("selected integration conflicts");
    assertThat(project.resolve(SENTINEL_TOML)).doesNotExist();
    assertThat(target).hasContent("user-owned\n");
  }

  @Test
  void initRejectsInvalidAndEmptySelectionsWithoutWriting() {
    assertThat(runInitWithInput("3\nx\n", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("Invalid integration selection");
    err.getBuffer().setLength(0);
    assertThat(runInitWithInput("3\n1\nx\n", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("Invalid quality-gate selection");
    err.getBuffer().setLength(0);
    assertThat(runInitWithInput("3\n1\n\n", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("Select at least one quality gate");
    assertThat(project.resolve(SENTINEL_TOML)).doesNotExist();
  }

  @Test
  void initRejectsInvalidArchitectureAndEof() {
    assertThat(runInitWithInput("3\n1\n9\nnope\n", "-C", project.toString()))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("Invalid architecture selection");
    err.getBuffer().setLength(0);
    assertThat(runInitWithInput("3\n1\n9\n10\n", "-C", project.toString()))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("Invalid architecture selection");
    err.getBuffer().setLength(0);
    assertThat(runInitWithInput("3\n1\n9\n", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("input ended");
  }

  @Test
  void checkSupportsProfilesAndStrictJson() throws IOException {
    run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, "tests,compile", "-C", project.toString());
    Files.writeString(
        project.resolve(SENTINEL_TOML),
        """
                version = 1
                 [quality-gates.tests]
                 command = "./mvnw test"
                 profiles = ["strict", "fast"]
                 [quality-gates.compile]
                 command = "./mvnw compile"
                 profiles = ["strict"]
                """);
    out.getBuffer().setLength(0);
    assertThat(run(CHECK, "--profile", "strict", FORMAT_OPTION, JSON, "-C", project.toString()))
        .isZero();
    assertThat(out.toString()).contains("\"policies\"", "\"status\" : \"PASSED\"");
    out.getBuffer().setLength(0);
    assertThat(
            run(CHECK, "--profile", "fast,strict", FORMAT_OPTION, JSON, "-C", project.toString()))
        .isZero();
    assertThat(out.toString()).contains("\"policies\"");
    out.getBuffer().setLength(0);
    assertThat(run(CHECK, "--profile", "fast", "--profile", "strict", "-C", project.toString()))
        .isZero();
    out.getBuffer().setLength(0);
    assertThat(run(CHECK, FORMAT_OPTION, JSON, "-C", project.toString()))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(out.toString()).contains("\"status\" : \"ERROR\"").doesNotContain("Unknown");
    out.getBuffer().setLength(0);
    assertThat(run(CHECK, "--profile", "unknown", FORMAT_OPTION, JSON, "-C", project.toString()))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(out.toString()).contains("\"status\" : \"ERROR\"");
  }

  @Test
  void checkReportsNewGatesAndMissingToolDoesNotStopOthers() throws IOException {
    Files.writeString(
        project.resolve(SENTINEL_TOML),
        """
                version = 1
                [quality-gates.gitleaks]
                command = ["./missing-gitleaks", "detect", "--redact"]
                [quality-gates.format]
                command = "./mvnw test"
                [quality-gates.enforcer]
                command = "./mvnw test"
                """);

    final int exit = run(CHECK, FORMAT_OPTION, JSON, "-C", project.toString());

    assertThat(exit).isNotZero();
    final JsonTree json = JsonTree.parse(out.toString());
    assertThat(json.get(CHECKS).size()).isEqualTo(3);
    assertThat(json.get(CHECKS).get(0).get("name").asString()).isEqualTo("gitleaks");
    assertThat(json.get(CHECKS).get(0).get(STATUS).asString()).isNotEqualTo("PASSED");
    assertThat(json.get(CHECKS).get(1).get("name").asString()).isEqualTo("format");
    assertThat(json.get(CHECKS).get(1).get(STATUS).asString()).isEqualTo("PASSED");
    assertThat(json.get(CHECKS).get(2).get("name").asString()).isEqualTo("enforcer");
    assertThat(json.get(CHECKS).get(2).get(STATUS).asString()).isEqualTo("PASSED");

    out.getBuffer().setLength(0);
    run(CHECK, "-C", project.toString());
    assertThat(out.toString()).contains("gitleaks", "format", "enforcer");
  }

  @Test
  void failFastReportsSkippedGatesAndJsonFlag() throws IOException {
    Files.createFile(project.resolve("FAIL"));
    Files.writeString(
        project.resolve(SENTINEL_TOML),
        """
            version = 1
            [quality-gates.tests]
            command = "./mvnw test"
            [quality-gates.compile]
            command = "./mvnw compile"
            """);

    assertThat(run(CHECK, "--fail-fast", "-C", project.toString())).isEqualTo(ExitCodes.FAILED);
    assertThat(out.toString())
        .contains("✗ tests", "– compile — not run (fail-fast)", "0 passed · 1 failed · 1 skipped");

    out.getBuffer().setLength(0);
    assertThat(run(CHECK, "--fail-fast", FORMAT_OPTION, JSON, "-C", project.toString()))
        .isEqualTo(ExitCodes.FAILED);
    JsonTree json = JsonTree.parse(out.toString());
    assertThat(json.get("failFast").asBoolean()).isTrue();
    assertThat(json.get(CHECKS).get(1).get(STATUS).asString()).isEqualTo("SKIPPED");
    assertThat(json.get(CHECKS).get(1).get("command").asString()).isEmpty();
    assertThat(json.get(CHECKS).get(1).get("output").asString()).isEmpty();
    assertThat(out.toString()).doesNotContain("Sentinel", "✓", "…");

    out.getBuffer().setLength(0);
    assertThat(run(CHECK, FORMAT_OPTION, JSON, "-C", project.toString()))
        .isEqualTo(ExitCodes.FAILED);
    json = JsonTree.parse(out.toString());
    assertThat(json.get("failFast").asBoolean()).isFalse();
    assertThat(json.get(CHECKS).get(1).get(STATUS).asString()).isEqualTo("FAILED");
  }
}
