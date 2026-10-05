package dev.sentinel.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.FakeCommandExecutor;
import dev.sentinel.domain.agent.AgentRequest;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessAgentRunnerFactoryTest {
  @Test
  void createsRunnerUsingTheRequestedWorkingDirectoryAndArguments() {
    FakeCommandExecutor executor = new FakeCommandExecutor(0, "completed", "");
    Path root = Path.of("project");
    var runner = new ProcessAgentRunnerFactory(executor).create(root, List.of("agent", "--yes"));

    assertThat(runner.run(new AgentRequest("fix the bug", 30)).succeeded()).isTrue();
    assertThat(executor.commands).containsExactly(List.of("agent", "--yes", "fix the bug"));
    assertThat(executor.workingDirectories).containsExactly(root);
  }
}
