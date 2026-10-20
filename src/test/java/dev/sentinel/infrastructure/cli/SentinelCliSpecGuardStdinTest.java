package dev.sentinel.infrastructure.cli;

import static dev.sentinel.infrastructure.cli.SentinelCliSpecGuardTest.APPROVED;
import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.AgentPayloadFixtures;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.application.spec.ApprovedArtifactGuardService;
import dev.sentinel.infrastructure.cli.spec.SpecGuardCommand;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import dev.sentinel.infrastructure.spec.FileSystemProjectPaths;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

/** {@code sentinel spec guard} reading patches and shell commands from stdin. */
class SentinelCliSpecGuardStdinTest extends SentinelCliSpecSupport {
  private static final int GUARD_PAYLOAD_LIMIT = 16 * 1024 * 1024;

  @BeforeEach
  void openSpecTree() throws IOException {
    SentinelCliSpecGuardTest.createOpenSpecTree(project);
  }

  private int guard(final InputStream input, final String... args) {
    final ApprovedArtifactGuardService service =
        new ApprovedArtifactGuardService(
            new ProjectDetector(new FileSystemProjectInspection()), new FileSystemProjectPaths());
    final CommandLine cli = new CommandLine(new SpecGuardCommand(service, input));
    cli.setOut(new PrintWriter(out, true));
    cli.setErr(new PrintWriter(err, true));
    cli.setExecutionExceptionHandler(CommandLineRunnerImpl::handle);
    return cli.execute(args);
  }

  private int guardWithInput(final String input, final String... args) {
    return guard(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), args);
  }

  @Test
  void refusedRecordedPatchNamesTheProtectedFile() {
    final String patch =
        AgentPayloadFixtures.json("opencode/apply_patch-multi-file.json")
            .get("args")
            .get("patchText")
            .asString();

    assertThat(guardWithInput(patch, DIRECTORY, project.toString(), "--patch-stdin"))
        .isEqualTo(ExitCodes.FAILED);
    assertThat(out.toString()).contains(APPROVED).doesNotContain("proposal.md");
  }

  @Test
  void shellCommandsUseTheWorkingDirectory() {
    assertThat(
            guardWithInput(
                "rm -rf specs/x",
                DIRECTORY,
                project.toString(),
                "--cwd",
                "openspec",
                "--shell-stdin"))
        .isEqualTo(ExitCodes.FAILED);
    assertThat(guardWithInput("ls openspec/specs", DIRECTORY, project.toString(), "--shell-stdin"))
        .isEqualTo(ExitCodes.OK);
  }

  @Test
  void uninspectableAndOversizedInputIsRefused() {
    assertThat(guardWithInput("not a patch", DIRECTORY, project.toString(), "--patch-stdin"))
        .isEqualTo(ExitCodes.FAILED);
    assertThat(out.toString()).contains("could not inspect the patch");

    final byte[] oversized = new byte[GUARD_PAYLOAD_LIMIT + 1];
    Arrays.fill(oversized, (byte) 'a');
    assertThat(
            guard(
                new ByteArrayInputStream(oversized),
                DIRECTORY,
                project.toString(),
                "--shell-stdin"))
        .isEqualTo(ExitCodes.FAILED);
    assertThat(out.toString()).contains("could not inspect the shell command");
  }
}
