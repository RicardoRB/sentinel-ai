package dev.sentinel.infrastructure.process;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.process.CommandResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@DisabledOnOs(OS.WINDOWS)
class ProcessCommandExecutorTest {
  private static final int SCRIPT_EXIT_CODE = 3;
  private static final int LARGE_OUTPUT_LINES = 20_000;

  private final ProcessCommandExecutor executor = new ProcessCommandExecutor();

  @Test
  void capturesStdoutStderrExitCodeAndDuration(@TempDir final Path dir) {
    final CommandResult result =
        executor.execute(
            List.of("sh", "-c", "echo out; echo err >&2; exit " + SCRIPT_EXIT_CODE), dir);

    assertThat(result.exitCode()).isEqualTo(SCRIPT_EXIT_CODE);
    assertThat(result.stdout()).isEqualTo("out\n");
    assertThat(result.stderr()).isEqualTo("err\n");
    assertThat(result.duration()).isPositive();
    assertThat(result.succeeded()).isFalse();
  }

  @Test
  void doesNotInterpretShellMetacharacters(@TempDir final Path dir) {
    final CommandResult result =
        executor.execute(List.of("echo", "a;", "echo", "b", "&&", "$HOME"), dir);

    assertThat(result.stdout()).isEqualTo("a; echo b && $HOME\n");
  }

  @Test
  void runsInWorkingDirectoryAndResolvesRelativeExecutableAgainstIt(@TempDir final Path dir)
      throws Exception {
    final Path script = dir.resolve("run.sh");
    Files.writeString(script, "#!/bin/sh\npwd -P\n");
    script.toFile().setExecutable(true);

    final CommandResult result = executor.execute(List.of("./run.sh"), dir);

    assertThat(result.exitCode()).isZero();
    assertThat(result.stdout().trim()).isEqualTo(dir.toRealPath().toString());
  }

  @Test
  void reportsFailureToStartInsteadOfThrowing(@TempDir final Path dir) {
    final CommandResult result = executor.execute(List.of("./does-not-exist"), dir);

    assertThat(result.exitCode()).isNotZero();
    assertThat(result.stderr()).contains("Could not start");
    assertThat(result.executionError()).startsWith("PROCESS_START_FAILURE");
  }

  @Test
  void reportsProjectRelativeNonExecutableAsStartFailure(@TempDir final Path dir) throws Exception {
    final Path script = dir.resolve("not-executable.sh");
    Files.writeString(script, "#!/bin/sh\nexit 0\n");

    final CommandResult result = executor.execute(List.of("./not-executable.sh"), dir);

    assertThat(result.exitCode()).isEqualTo(-1);
    assertThat(result.executionError()).isEqualTo("PROCESS_START_FAILURE");
  }

  @Test
  void handlesLargeOutputWithoutDeadlock(@TempDir final Path dir) {
    final CommandResult result =
        executor.execute(
            List.of(
                "sh",
                "-c",
                "i=0; while [ $i -lt "
                    + LARGE_OUTPUT_LINES
                    + " ]; do echo line-$i; echo err-$i >&2; i=$((i+1)); done"),
            dir);

    assertThat(result.exitCode()).isZero();
    assertThat(result.stdout().lines().count()).isEqualTo(LARGE_OUTPUT_LINES);
    assertThat(result.stderr().lines().count()).isEqualTo(LARGE_OUTPUT_LINES);
  }
}
