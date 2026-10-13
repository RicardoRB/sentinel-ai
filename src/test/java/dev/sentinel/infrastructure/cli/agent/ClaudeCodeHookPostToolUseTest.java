package dev.sentinel.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.infrastructure.cli.ExitCodes;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClaudeCodeHookPostToolUseTest extends ClaudeCodeHookTestSupport {
  @Test
  void bothFailuresReachTheAgent() {
    testsPass = false;
    specsValid = false;

    assertThat(hook(postToolUse("openspec/changes/foo/tasks.md"))).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("tests: ", "openspec change foo: 1 error");
  }

  @Test
  void learningPromptAndSpecFindingsAreBothReported() {
    testsPass = false;
    hook(postToolUse("src/App.java"));
    err.getBuffer().setLength(0);
    testsPass = true;
    specsValid = false;

    assertThat(hook(postToolUse("openspec/changes/foo/tasks.md"))).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("openspec change foo: 1 error", "AGENTS.md");
    assertThat(out.toString()).isEmpty();
  }

  @Test
  void nonSpecEditKeepsTheExistingBehavior() {
    specsValid = false;

    assertThat(hook(postToolUse("src/main/java/App.java"))).isEqualTo(ExitCodes.OK);
    assertThat(commands).containsExactly(List.of("mvn", "test"));
    assertThat(out.toString()).isEmpty();
    assertThat(err.toString()).isEmpty();
  }
}
