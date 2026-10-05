package dev.sentinel.infrastructure.process;

import dev.sentinel.domain.process.CommandResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledOnOs(OS.WINDOWS)
class ProcessCommandExecutorTest {

    private final ProcessCommandExecutor executor = new ProcessCommandExecutor();

    @Test
    void capturesStdoutStderrExitCodeAndDuration(@TempDir Path dir) {
        CommandResult result = executor.execute(List.of("sh", "-c", "echo out; echo err >&2; exit 3"), dir);

        assertThat(result.exitCode()).isEqualTo(3);
        assertThat(result.stdout()).isEqualTo("out\n");
        assertThat(result.stderr()).isEqualTo("err\n");
        assertThat(result.duration()).isPositive();
        assertThat(result.succeeded()).isFalse();
    }

    @Test
    void doesNotInterpretShellMetacharacters(@TempDir Path dir) {
        CommandResult result = executor.execute(List.of("echo", "a;", "echo", "b", "&&", "$HOME"), dir);

        assertThat(result.stdout()).isEqualTo("a; echo b && $HOME\n");
    }

    @Test
    void runsInWorkingDirectoryAndResolvesRelativeExecutableAgainstIt(@TempDir Path dir) throws Exception {
        Path script = dir.resolve("run.sh");
        Files.writeString(script, "#!/bin/sh\npwd -P\n");
        script.toFile().setExecutable(true);

        CommandResult result = executor.execute(List.of("./run.sh"), dir);

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout().trim()).isEqualTo(dir.toRealPath().toString());
    }

    @Test
    void reportsFailureToStartInsteadOfThrowing(@TempDir Path dir) {
        CommandResult result = executor.execute(List.of("./does-not-exist"), dir);

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("Could not start");
    }

    @Test
    void handlesLargeOutputWithoutDeadlock(@TempDir Path dir) {
        CommandResult result = executor.execute(
                List.of("sh", "-c", "i=0; while [ $i -lt 20000 ]; do echo line-$i; echo err-$i >&2; i=$((i+1)); done"), dir);

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout().lines().count()).isEqualTo(20000);
        assertThat(result.stderr().lines().count()).isEqualTo(20000);
    }
}
