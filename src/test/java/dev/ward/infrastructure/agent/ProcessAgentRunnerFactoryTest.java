package dev.ward.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.agent.AgentRequest;
import dev.ward.domain.agent.AgentRunner;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessAgentRunnerFactoryTest {
  private static final int TIMEOUT_SECONDS = 30;

  @Test
  void createsRunnerUsingTheRequestedWorkingDirectoryAndArguments() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "completed", "");
    final Path root = Path.of("project");
    final AgentRunner runner =
        new ProcessAgentRunnerFactory(executor).create(root, List.of("agent", "--yes"));

    assertThat(runner.run(new AgentRequest("fix the bug", TIMEOUT_SECONDS)).succeeded()).isTrue();
    assertThat(executor.commands).containsExactly(List.of("agent", "--yes", "fix the bug"));
    assertThat(executor.workingDirectories).containsExactly(root);
  }
}
