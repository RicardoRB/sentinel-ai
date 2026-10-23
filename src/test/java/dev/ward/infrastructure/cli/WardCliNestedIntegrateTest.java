package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** `ward integrate` and `ward doctor` treat a nested project like `ward init` does. */
@DisabledOnOs(OS.WINDOWS)
class WardCliNestedIntegrateTest extends WardCliIntegrationSupport {
  private static final String CLAUDE = "claude-code";
  private static final String DOCTOR = "doctor";

  @TempDir Path repository;

  private Path api;

  @BeforeEach
  void nestedProject() throws IOException {
    Files.createDirectories(repository.resolve(".git"));
    api = Files.createDirectories(repository.resolve("apps/api"));
    Files.copy(project.resolve("pom.xml"), api.resolve("pom.xml"));
  }

  @Test
  void integrateInstallsAtTheRepositoryRootAndDoctorFindsIt() {
    assertThat(run(DOCTOR, "-C", api.toString())).as(out.toString()).isNotNegative();
    assertThat(out.toString()).contains("No supported agent integration configured");
    out.getBuffer().setLength(0);

    assertThat(run("integrate", CLAUDE, "-C", api.toString())).as(err.toString()).isZero();

    assertThat(repository.resolve(".claude/hooks/ward-edit-write-apps-api")).exists();
    assertThat(repository.resolve(".claude/settings.json")).exists();
    assertThat(api.resolve(".claude")).doesNotExist();
    out.getBuffer().setLength(0);
    run(DOCTOR, "-C", api.toString());
    assertThat(out.toString()).contains("claude-code=installed");

    assertThat(run("integrate", CLAUDE, "--remove", "-C", api.toString())).isZero();
    assertThat(repository.resolve(".claude/hooks/ward-edit-write-apps-api")).doesNotExist();
  }
}
