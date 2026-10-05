package dev.sentinel.application.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.FakeCommandExecutor;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class EditWriteGuardRunnerTest {
  @Test
  void runsJsonCheckWithArgumentVectorAndProjectRoot() {
    FakeCommandExecutor executor = new FakeCommandExecutor(0, "{\"status\":\"PASSED\"}", "");
    EditWriteGuardRunner.GuardRunResult result =
        new EditWriteGuardRunner(executor).run(Path.of("/tmp/project"));

    assertThat(result.succeeded()).isTrue();
    assertThat(result.output()).contains("PASSED");
    assertThat(executor.commands).containsExactly(List.of("sentinel", "check", "--format", "json"));
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
