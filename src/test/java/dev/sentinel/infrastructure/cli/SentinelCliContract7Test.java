package dev.sentinel.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.JsonTree;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

@DisabledOnOs(OS.WINDOWS)
class SentinelCliContract7Test extends SentinelCliIntegrationSupport {
  @Test
  void hookFailingCheckExitsTwoAndWritesFailureToStderr() throws IOException {
    run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, TESTS, "-C", project.toString());
    fail();
    out.getBuffer().setLength(0);
    err.getBuffer().setLength(0);

    assertThat(run("hook", CLAUDE_CODE, LEARN_AFTER, "1", "-C", project.toString()))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("tests: FAILED");
    assertThat(out.toString()).isEmpty();
  }

  @Test
  void hookPassingWithPromptsEmitsPostToolUseJson() throws IOException {
    run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, TESTS, "-C", project.toString());
    fail();
    assertThat(run("hook", CLAUDE_CODE, LEARN_AFTER, "1", "-C", project.toString()))
        .isEqualTo(ExitCodes.ERROR);
    pass();
    out.getBuffer().setLength(0);
    err.getBuffer().setLength(0);

    assertThat(run("hook", CLAUDE_CODE, LEARN_AFTER, "1", "-C", project.toString()))
        .isEqualTo(ExitCodes.OK);
    final JsonTree hook = JsonTree.parse(out.toString());
    assertThat(hook.get("hookSpecificOutput").get("hookEventName").asString())
        .isEqualTo("PostToolUse");
    final String context = hook.get("hookSpecificOutput").get("additionalContext").asString();
    assertThat(context).contains("AGENTS.md", TESTS);
    assertThat(out.toString()).doesNotContain("\u001b");
  }

  @Test
  void hookPassingWithoutPromptsEmitsNothing() throws IOException {
    run(INIT, INTEGRATION_OPTION, NONE, GATE_OPTION, TESTS, "-C", project.toString());
    out.getBuffer().setLength(0);
    err.getBuffer().setLength(0);

    assertThat(run("hook", CLAUDE_CODE, LEARN_AFTER, "1", "-C", project.toString()))
        .isEqualTo(ExitCodes.OK);
    assertThat(out.toString()).isEmpty();
    assertThat(err.toString()).isEmpty();
  }

  @Test
  void hookOnProjectWithoutConfigurationExitsTwo() {
    out.getBuffer().setLength(0);
    err.getBuffer().setLength(0);

    assertThat(run("hook", CLAUDE_CODE, "-C", project.toString())).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("sentinel:", "sentinel init");
    assertThat(out.toString()).isEmpty();
  }
}
