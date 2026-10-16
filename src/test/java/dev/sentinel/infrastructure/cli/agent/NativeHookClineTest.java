package dev.sentinel.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.infrastructure.agent.ClineIntegration;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins Cline v3 delivery: PreToolUse cancel, PostToolUse contextModification. */
class NativeHookClineTest {
  @TempDir Path root;

  private NativeHookRun hook;

  @BeforeEach
  void project() throws IOException {
    hook = new NativeHookRun(root, new ClineIntegration());
  }

  private String pre(final String tool, final String parameters) {
    return "{\"clineVersion\":\"3.0.72\",\"hookName\":\"tool_call\",\"workspaceRoots\":[\""
        + root
        + "\"],\"tool_call\":{\"id\":\"1\",\"name\":\""
        + tool
        + "\",\"input\":{}},\"preToolUse\":{\"toolName\":\""
        + tool
        + "\",\"parameters\":"
        + parameters
        + "}}";
  }

  @Test
  void theGuardCancelsAnEditToAnApprovedSpec() {
    final String spec = root.resolve("openspec/specs/auth/spec.md").toString();
    assertThat(
            hook.run(
                0,
                pre("editor", "{\"path\":\"" + spec + "\",\"new_text\":\"x\"}"),
                "--guard-approved-specs"))
        .isZero();
    assertThat(hook.out.toString())
        .startsWith("{\"cancel\":true,\"errorMessage\":")
        .contains("openspec/specs/auth/spec.md");
  }

  @Test
  void theGuardReadsEachCommandOfARunCommandsCall() {
    assertThat(
            hook.run(
                0,
                pre(
                    "run_commands",
                    "{\"commands\":\"[\\\"ls\\\",\\\"rm openspec/specs/auth/spec.md\\\"]\"}"),
                "--guard-approved-specs"))
        .isZero();
    assertThat(hook.out.toString()).startsWith("{\"cancel\":true");
  }

  @Test
  void commandsInAnUnexpectedShapeAreUnreadableAndCancelled() {
    assertThat(
            hook.run(
                0,
                pre("run_commands", "{\"commands\":\"[{\\\"command\\\":\\\"rm\\\"}]\"}"),
                "--guard-approved-specs"))
        .isZero();
    assertThat(hook.out.toString()).startsWith("{\"cancel\":true");
  }

  @Test
  void theGuardAllowsAPatchToAnActiveChange() {
    final String patch =
        "*** Begin Patch\\n*** Add File: openspec/changes/x/tasks.md\\n+- [ ] 1\\n*** End Patch";
    assertThat(
            hook.run(
                0, pre("apply_patch", "{\"input\":\"" + patch + "\"}"), "--guard-approved-specs"))
        .isZero();
    assertThat(hook.out.toString()).isEmpty();
  }

  @Test
  void failingChecksAfterAnEditReachTheAgentAsContextModification() {
    final String payload =
        "{\"hookName\":\"tool_result\",\"postToolUse\":{\"toolName\":\"editor\","
            + "\"parameters\":{\"path\":\"src/A.java\"},\"result\":\"ok\",\"success\":true}}";
    assertThat(hook.run(1, payload)).isZero();
    assertThat(hook.out.toString())
        .startsWith("{\"contextModification\":")
        .contains("Sentinel quality findings");
  }

  @Test
  void aJsonNullPayloadIsUnreadable() {
    assertThat(hook.run(0, "null", "--guard-approved-specs")).isZero();
    assertThat(hook.out.toString()).startsWith("{\"cancel\":true");
  }
}
