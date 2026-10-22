package dev.ward.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.agent.CursorIntegration;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins Cursor delivery: postToolUse additional_context, stop followup_message, preToolUse deny. */
class NativeHookCursorTest {
  @TempDir Path root;

  private NativeHookRun hook;

  @BeforeEach
  void project() throws IOException {
    hook = new NativeHookRun(root, new CursorIntegration());
  }

  private String tool(final String event, final String name, final String input) {
    return "{\"conversation_id\":\"c\",\"generation_id\":\"g\",\"hook_event_name\":\""
        + event
        + "\",\"workspace_roots\":[\""
        + root
        + "\"],\"cwd\":\""
        + root
        + "\",\"tool_name\":\""
        + name
        + "\",\"tool_input\":"
        + input
        + "}";
  }

  @Test
  void failingChecksAfterAWriteReachTheAgentAsAdditionalContext() {
    assertThat(
            hook.run(
                1,
                tool(
                    "postToolUse",
                    "Write",
                    "{\"file_path\":\"" + root.resolve("src/A.java") + "\",\"content\":\"x\"}")))
        .isZero();
    assertThat(hook.out.toString())
        .startsWith("{\"additional_context\":")
        .contains("Ward quality findings");
    assertThat(hook.err.toString()).isEmpty();
  }

  @Test
  void failingChecksAtStopSubmitAFollowUpOnce() {
    assertThat(
            hook.run(1, "{\"hook_event_name\":\"stop\",\"status\":\"completed\",\"loop_count\":0}"))
        .isZero();
    assertThat(hook.out.toString()).startsWith("{\"followup_message\":");
    assertThat(
            hook.run(1, "{\"hook_event_name\":\"stop\",\"status\":\"completed\",\"loop_count\":1}"))
        .isZero();
    assertThat(hook.out.toString()).isEmpty();
    assertThat(hook.err.toString()).contains("Ward quality findings");
  }

  @Test
  void theGuardDeniesAWriteToAnApprovedSpec() {
    final String spec = root.resolve("openspec/specs/auth/spec.md").toString();
    assertThat(
            hook.run(
                0,
                tool("preToolUse", "Write", "{\"file_path\":\"" + spec + "\",\"content\":\"x\"}"),
                "--guard-approved-specs"))
        .isZero();
    assertThat(hook.out.toString())
        .startsWith("{\"permission\":\"deny\"")
        .contains("openspec/specs/auth/spec.md");
  }

  @Test
  void theGuardResolvesAShellCommandInItsWorkingDirectory() {
    assertThat(
            hook.run(
                0,
                tool(
                    "preToolUse",
                    "Shell",
                    "{\"command\":\"rm specs/auth/spec.md\",\"working_directory\":\""
                        + root.resolve("openspec")
                        + "\"}"),
                "--guard-approved-specs"))
        .isZero();
    assertThat(hook.out.toString()).contains("\"permission\":\"deny\"");
  }

  @Test
  void theGuardAllowsAWriteOutsideApprovedSpecs() {
    assertThat(
            hook.run(
                0,
                tool("preToolUse", "Write", "{\"file_path\":\"src/A.java\"}"),
                "--guard-approved-specs"))
        .isZero();
    assertThat(hook.out.toString()).isEmpty();
  }
}
