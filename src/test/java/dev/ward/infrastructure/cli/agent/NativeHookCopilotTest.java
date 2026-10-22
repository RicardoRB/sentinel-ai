package dev.ward.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.agent.CopilotIntegration;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins Copilot CLI feedback delivery: post-edit findings travel as additionalContext. */
class NativeHookCopilotTest {
  @TempDir Path root;

  private NativeHookRun hook;

  @BeforeEach
  void project() throws IOException {
    hook = new NativeHookRun(root, new CopilotIntegration());
  }

  private String edit(final String event, final String path) {
    return "{\"hook_event_name\":\""
        + event
        + "\",\"session_id\":\"s\",\"timestamp\":\"t\",\"cwd\":\""
        + root
        + "\",\"tool_name\":\"edit\",\"tool_input\":{\"path\":\""
        + path
        + "\",\"old_str\":\"a\",\"new_str\":\"b\"}}";
  }

  @Test
  void failingChecksAfterAnEditReachTheAgentAsAdditionalContext() {
    assertThat(hook.run(1, edit("PostToolUse", root.resolve("src/A.java").toString()))).isZero();
    assertThat(hook.out.toString()).startsWith("{\"additionalContext\":");
    assertThat(hook.out.toString()).contains("Ward quality findings");
    assertThat(hook.err.toString()).isEmpty();
  }

  @Test
  void aCleanEditIsSilent() {
    assertThat(hook.run(0, edit("PostToolUse", root.resolve("src/A.java").toString()))).isZero();
    assertThat(hook.out.toString()).isEmpty();
  }

  @Test
  void failingChecksAtStopContinueTheTurnOnce() {
    assertThat(hook.run(1, "{\"hook_event_name\":\"Stop\",\"stop_hook_active\":false}")).isZero();
    assertThat(hook.out.toString()).startsWith("{\"decision\":\"block\",\"reason\":");
    assertThat(hook.run(1, "{\"hook_event_name\":\"Stop\",\"stop_hook_active\":true}")).isZero();
    assertThat(hook.out.toString()).isEmpty();
    assertThat(hook.err.toString()).contains("Ward quality findings");
  }

  @Test
  void theGuardDeniesAnAbsoluteEditPathInsideApprovedSpecs() {
    final String spec = root.resolve("openspec/specs/auth/spec.md").toString();
    assertThat(hook.run(0, edit("PreToolUse", spec), "--guard-approved-specs")).isZero();
    assertThat(hook.out.toString())
        .startsWith("{\"permissionDecision\":\"deny\",\"permissionDecisionReason\":")
        .contains("openspec/specs/auth/spec.md");
  }

  @Test
  void theGuardDeniesAProtectedShellCommand() {
    assertThat(
            hook.run(
                0,
                "{\"hook_event_name\":\"PreToolUse\",\"cwd\":\""
                    + root
                    + "\",\"tool_name\":\"bash\",\"tool_input\":{\"command\":\"rm openspec/specs/auth/spec.md\"}}",
                "--guard-approved-specs"))
        .isZero();
    assertThat(hook.out.toString()).contains("\"permissionDecision\":\"deny\"");
  }

  @Test
  void theGuardAllowsAnEditOutsideApprovedSpecs() {
    assertThat(
            hook.run(
                0,
                edit("PreToolUse", root.resolve("src/A.java").toString()),
                "--guard-approved-specs"))
        .isZero();
    assertThat(hook.out.toString()).isEmpty();
  }
}
