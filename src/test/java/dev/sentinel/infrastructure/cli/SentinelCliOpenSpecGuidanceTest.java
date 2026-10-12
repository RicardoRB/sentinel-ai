package dev.sentinel.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.infrastructure.agent.ClaudeCodeIntegration;
import dev.sentinel.infrastructure.agent.FileSystemOpenSpecSetupInspection;
import dev.sentinel.infrastructure.agent.OpenCodeIntegration;
import dev.sentinel.infrastructure.cli.logging.LoggingScope;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

@DisabledOnOs(OS.WINDOWS)
class SentinelCliOpenSpecGuidanceTest extends SentinelCliIntegrationSupport {
  private static final String GUIDANCE = "--openspec-guidance";
  private static final String CONFIG = "schema: spec-driven\n# human-owned\n";

  @TempDir Path bin;
  private Path invoked;

  @BeforeEach
  void installRecordingOpenSpecStub() throws IOException {
    invoked = bin.resolve("openspec-was-run");
    final Path stub = bin.resolve("openspec");
    Files.writeString(stub, "#!/bin/sh\ntouch '" + invoked + "'\n");
    Files.setPosixFilePermissions(stub, PosixFilePermissions.fromString("rwxr-xr-x"));
  }

  @Test
  void installAndRemoveLeaveOpenSpecSetupUntouched() throws IOException {
    final Path config = project.resolve("openspec/config.yaml");
    Files.createDirectories(config.getParent());
    Files.writeString(config, CONFIG);
    final byte[] before = Files.readAllBytes(config);

    assertThat(integrate(bin.toString(), "claude-code", GUIDANCE)).isZero();
    assertThat(project.resolve(ClaudeCodeIntegration.GUIDANCE_FILE)).exists();
    assertThat(out.toString()).contains("WARNING:", "openspec config profile");
    assertThat(integrate(bin.toString(), "claude-code", GUIDANCE, "--remove")).isZero();
    assertThat(integrate(bin.toString(), "opencode", GUIDANCE)).isZero();
    assertThat(integrate(bin.toString(), "opencode", "--remove")).isZero();

    assertThat(Files.readAllBytes(config)).isEqualTo(before);
    assertThat(invoked).doesNotExist();
  }

  @Test
  void missingProjectSetupNeverCreatesOpenSpecDirectory() {
    assertThat(integrate(bin.toString(), "claude-code", GUIDANCE)).isEqualTo(ExitCodes.ERROR);

    assertThat(out.toString()).contains("UNAVAILABLE", "openspec init");
    assertThat(project.resolve("openspec")).doesNotExist();
    assertThat(project.resolve(".claude")).doesNotExist();
    assertThat(invoked).doesNotExist();
  }

  @Test
  void missingExecutableWritesNothing() throws IOException {
    Files.createDirectory(project.resolve("openspec"));

    assertThat(integrate(project.resolve("empty-bin").toString(), "opencode", GUIDANCE))
        .isEqualTo(ExitCodes.ERROR);

    assertThat(out.toString()).contains("UNAVAILABLE", "not on PATH");
    assertThat(project.resolve(".opencode")).doesNotExist();
  }

  private int integrate(final String path, final String... args) {
    final CommandLine cli =
        IntegrateCommandFixture.commandLine(
            new ByteArrayInputStream(new byte[0]),
            new FileSystemOpenSpecSetupInspection(() -> path),
            new PrintWriter(out, true),
            new PrintWriter(err, true),
            new CommandLine.RunLast());
    final List<String> arguments = new ArrayList<>(List.of(args));
    arguments.addAll(List.of("-C", project.toString()));
    return cli.execute(arguments.toArray(String[]::new));
  }

  @Test
  void verboseDiagnosticsGoOnlyToStderrWithoutSensitiveContent() throws IOException {
    final Path config = project.resolve("openspec/config.yaml");
    Files.createDirectories(config.getParent());
    Files.writeString(config, CONFIG);
    final Path userGuidance = project.resolve(OpenCodeIntegration.GUIDANCE_FILE);

    try (LoggingScope ignored = LoggingScope.open(new PrintWriter(err, true), true)) {
      integrate(bin.toString(), "claude-code", GUIDANCE);
      integrate(bin.toString(), "claude-code", GUIDANCE);
      integrate(bin.toString(), "claude-code", "--remove");
      Files.createDirectories(userGuidance.getParent());
      Files.writeString(userGuidance, "user guidance\n");
      integrate(bin.toString(), "opencode", GUIDANCE);
    }

    assertThat(err.toString())
        .contains("event=integration-start agent=claude-code operation=install guidance=included")
        .contains("event=openspec-prerequisite agent=claude-code outcome=ok")
        .contains("event=openspec-verify-workflow agent=claude-code present=false")
        .contains("event=integration-complete agent=claude-code status=changed")
        .contains("status=already_present")
        .contains("operation=remove guidance=included")
        .contains("status=removed")
        .contains("event=integration-conflict agent=opencode");
    assertThat(out.toString()).doesNotContain("event=");
    assertThat(err.toString())
        .doesNotContain("Working through OpenSpec", "human-owned", "user guidance")
        .doesNotContain(bin.toString());
  }
}
