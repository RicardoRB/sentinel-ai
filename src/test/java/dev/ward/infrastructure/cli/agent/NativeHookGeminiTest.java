package dev.ward.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.agent.GeminiIntegration;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins Gemini CLI 0.64.0 delivery: AfterTool context, AfterAgent retry, BeforeTool denial. */
class NativeHookGeminiTest {
  @TempDir Path root;

  private NativeHookRun hook;

  @BeforeEach
  void project() throws IOException {
    hook = new NativeHookRun(root, new GeminiIntegration());
  }

  private String tool(final String event, final String name, final String input) {
    return "{\"session_id\":\"s\",\"transcript_path\":\"t\",\"cwd\":\""
        + root
        + "\",\"hook_event_name\":\""
        + event
        + "\",\"timestamp\":\"2026-10-16T00:00:00Z\",\"tool_name\":\""
        + name
        + "\",\"tool_input\":"
        + input
        + "}";
  }

  @Test
  void failingChecksAfterAnEditAreAppendedToTheToolResult() {
    assertThat(hook.run(1, tool("AfterTool", "replace", "{\"file_path\":\"src/A.java\"}")))
        .isZero();
    assertThat(hook.out.toString())
        .startsWith(
            "{\"hookSpecificOutput\":{\"hookEventName\":\"AfterTool\",\"additionalContext\":")
        .contains("Ward quality findings");
    assertThat(hook.err.toString()).isEmpty();
  }

  @Test
  void failingChecksAtAfterAgentRetryTheTurnOnce() {
    assertThat(hook.run(1, "{\"hook_event_name\":\"AfterAgent\",\"stop_hook_active\":false}"))
        .isZero();
    assertThat(hook.out.toString()).startsWith("{\"decision\":\"block\",\"reason\":");
    assertThat(hook.run(1, "{\"hook_event_name\":\"AfterAgent\",\"stop_hook_active\":true}"))
        .isZero();
    assertThat(hook.out.toString()).isEmpty();
    assertThat(hook.err.toString()).contains("Ward quality findings");
  }

  @Test
  void theGuardDeniesAWriteToAnApprovedSpec() {
    assertThat(
            hook.run(
                0,
                tool(
                    "BeforeTool",
                    "write_file",
                    "{\"file_path\":\"openspec/specs/auth/spec.md\",\"content\":\"x\"}"),
                "--guard-approved-specs"))
        .isZero();
    assertThat(hook.out.toString())
        .startsWith("{\"decision\":\"deny\",\"reason\":")
        .contains("openspec/specs/auth/spec.md");
  }

  @Test
  void theGuardDeniesAProtectedShellCommand() {
    assertThat(
            hook.run(
                0,
                tool(
                    "BeforeTool",
                    "run_shell_command",
                    "{\"command\":\"rm openspec/specs/auth/spec.md\"}"),
                "--guard-approved-specs"))
        .isZero();
    assertThat(hook.out.toString()).contains("\"decision\":\"deny\"");
  }

  @Test
  void theGuardAllowsAnEditOutsideApprovedSpecs() {
    assertThat(
            hook.run(
                0,
                tool("BeforeTool", "replace", "{\"file_path\":\"src/A.java\"}"),
                "--guard-approved-specs"))
        .isZero();
    assertThat(hook.out.toString()).isEmpty();
  }
}
