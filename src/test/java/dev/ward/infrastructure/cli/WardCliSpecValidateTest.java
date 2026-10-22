package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@DisabledOnOs(OS.WINDOWS)
class WardCliSpecValidateTest extends WardCliSpecSupport {
  @Test
  void cleanTargetedValidationPrintsNothing() throws IOException {
    configure(active());
    Files.createDirectories(project.resolve("openspec/changes/good"));

    assertThat(run(SPEC, VALIDATE, DIRECTORY, project.toString(), "openspec/changes/good/a.md"))
        .isEqualTo(ExitCodes.OK);
    assertThat(out.toString()).isEmpty();
    assertThat(calls()).isEqualTo("validate good --type change --strict --no-interactive\n");
  }

  @Test
  void targetedFindingsArePrintedBoundedAndOrdered() throws IOException {
    configure(active());
    Files.createDirectories(project.resolve("openspec/changes/bad"));

    assertThat(
            run(
                SPEC,
                VALIDATE,
                DIRECTORY,
                project.toString(),
                project.resolve("openspec/changes/bad/proposal.md").toString()))
        .isEqualTo(ExitCodes.FAILED);
    assertThat(out.toString())
        .isEqualTo(
            """
            openspec change bad: 1 error, 1 warning
              ERROR x/spec.md: ADDED "Thing" must include at least one scenario
              WARNING x/spec.md: ADDED "Thing" should contain SHALL or MUST
            """);
  }

  @Test
  void archivedAndUnrelatedPathsLaunchNothing() throws IOException {
    configure(active());
    Files.createDirectories(project.resolve("openspec/changes/archive/2026-10-05-old"));

    assertThat(
            run(
                SPEC,
                VALIDATE,
                DIRECTORY,
                project.toString(),
                "openspec/changes/archive/2026-10-05-old/tasks.md",
                "pom.xml"))
        .isEqualTo(ExitCodes.OK);
    assertThat(out.toString()).isEmpty();
    assertThat(project.resolve("calls.log")).doesNotExist();
  }

  @Test
  void disabledOpenSpecIsANoOp() throws IOException {
    stub();
    Files.writeString(
        project.resolve(WARD_TOML),
        "version = 1\n[quality-gates.tests]\ncommand = \"./mvnw test\"\n");
    Files.createDirectories(project.resolve("openspec/changes/bad"));

    assertThat(run(SPEC, VALIDATE, DIRECTORY, project.toString(), "openspec/changes/bad/a.md"))
        .isEqualTo(ExitCodes.OK);
    assertThat(run(SPEC, VALIDATE, DIRECTORY, project.toString())).isEqualTo(ExitCodes.OK);
    assertThat(out.toString()).isEmpty();
    assertThat(project.resolve("calls.log")).doesNotExist();
  }

  @Test
  void fullValidationRunsConfiguredSpecGatesOnly() throws IOException {
    final Path executable = stub();
    configure(
        active()
            + """
            [quality-gates.openspec-archived]
            command = ["%s", "validate", "--archived", "--no-interactive"]
            [quality-gates.tests]
            command = "./mvnw test"
            """
                .formatted(executable));

    assertThat(run(SPEC, VALIDATE, DIRECTORY, project.toString())).isEqualTo(ExitCodes.FAILED);
    assertThat(out.toString())
        .isEqualTo(
            """
            openspec-archived (FAILED): 1 error
              ERROR Change 'old' has incomplete tasks
            """);
    assertThat(calls())
        .isEqualTo(
            "validate --all --strict --no-interactive\nvalidate --archived --no-interactive\n");
  }

  @Test
  void missingProjectIsAnError(@TempDir final Path empty) {
    assertThat(run(SPEC, VALIDATE, DIRECTORY, empty.toString(), "a.md")).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).startsWith("ward: ");
    assertThat(out.toString()).isEmpty();
  }

  @Test
  void specWithoutSubcommandShowsUsage() {
    assertThat(run(SPEC)).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("validate");
  }
}
