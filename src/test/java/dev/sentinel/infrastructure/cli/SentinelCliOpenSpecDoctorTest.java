package dev.sentinel.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.WINDOWS)
class SentinelCliOpenSpecDoctorTest extends SentinelCliIntegrationSupport {
  @Test
  void doctorReportsMissingOpenSpecPrerequisites(
      @org.junit.jupiter.api.io.TempDir final Path ignored) throws IOException {
    Files.writeString(
        project.resolve(SENTINEL_TOML),
        """
                version = 1
                [quality-gates.openspec]
                enabled = true
                command = ["missing-openspec", "validate", "--no-interactive"]
                """);
    Files.createDirectories(project.resolve("openspec"));

    assertThat(run("doctor", "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(out.toString()).contains("openspec", "executable unavailable");
  }

  @Test
  void doctorReportsAvailableOpenSpecPrerequisitesAndNonDefaultRegistration() throws IOException {
    final Path executable = project.resolve("openspec-test");
    Files.writeString(executable, "#!/bin/sh\nexit 0\n");
    executable.toFile().setExecutable(true);
    Files.createDirectories(project.resolve("openspec"));
    Files.writeString(
        project.resolve(SENTINEL_TOML),
        """
                version = 1
                [quality-gates.openspec-archived]
                enabled = true
                command = ["%s", "validate", "--archived", "--no-interactive"]
                """
            .formatted(executable));

    assertThat(run("doctor", "-C", project.toString())).isEqualTo(ExitCodes.OK);
    assertThat(out.toString()).contains("openspec-archived", "OK");
  }
}
