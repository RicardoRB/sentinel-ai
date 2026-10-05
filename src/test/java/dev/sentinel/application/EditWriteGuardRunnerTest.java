package dev.sentinel.application;

import dev.sentinel.domain.FakeCommandExecutor;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class EditWriteGuardRunnerTest {
    @Test
    void runsJsonCheckWithArgumentVectorAndProjectRoot() {
        FakeCommandExecutor executor = new FakeCommandExecutor(0, "{\"status\":\"PASSED\"}", "");
        EditWriteGuardRunner.GuardRunResult result = new EditWriteGuardRunner(executor)
                .run(Path.of("/tmp/project"));

        assertThat(result.succeeded()).isTrue();
        assertThat(result.output()).contains("PASSED");
        assertThat(executor.commands).containsExactly(java.util.List.of("sentinel", "check", "--format", "json"));
        assertThat(executor.workingDirectories).containsExactly(Path.of("/tmp/project"));
    }

    @Test
    void propagatesFailedAndExecutionErrorResults() {
        FakeCommandExecutor failed = new FakeCommandExecutor(1, "{\"status\":\"FAILED\"}", "");
        EditWriteGuardRunner.GuardRunResult result = new EditWriteGuardRunner(failed).run(Path.of("."));
        assertThat(result.succeeded()).isFalse();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.output()).contains("FAILED");
    }
}
