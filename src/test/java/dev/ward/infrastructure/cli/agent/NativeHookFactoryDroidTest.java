package dev.ward.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.agent.FactoryDroidIntegration;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins Factory Droid 0.243.0 delivery: post-tool exit 2, Stop continuation, PreToolUse denial. */
class NativeHookFactoryDroidTest {
  @TempDir Path root;

  private NativeHookRun hook;

  @BeforeEach
  void project() throws IOException {
    hook = new NativeHookRun(root, new FactoryDroidIntegration());
  }

  private String tool(final String event, final String name, final String input) {
    return "{\"session_id\":\"s\",\"transcript_path\":\"t\",\"cwd\":\""
        + root
        + "\",\"permission_mode\":\"auto-low\",\"hook_event_name\":\""
        + event
        + "\",\"tool_name\":\""
        + name
        + "\",\"tool_input\":"
        + input
        + "}";
  }

  @Test
  void failingChecksAfterAnEditExitTwoWithTheFindingsOnStderr() {
    assertThat(hook.run(1, tool("PostToolUse", "Edit", "{\"file_path\":\"src/A.java\"}")))
        .isEqualTo(2);
    assertThat(hook.err.toString()).contains("Ward quality findings");
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
  void theGuardDeniesAnEditToAnApprovedSpec() {
    assertThat(
            hook.run(
                0,
                tool("PreToolUse", "Edit", "{\"file_path\":\"openspec/specs/auth/spec.md\"}"),
                "--guard-approved-specs"))
        .isZero();
    assertThat(hook.out.toString())
        .startsWith("{\"hookSpecificOutput\":{\"hookEventName\":\"PreToolUse\"")
        .contains("\"permissionDecision\":\"deny\"", "openspec/specs/auth/spec.md");
  }

  @Test
  void theGuardDeniesAPatchToAnApprovedSpec() {
    final String patch =
        "*** Begin Patch\\n*** Update File: openspec/specs/auth/spec.md\\n@@\\n-# Auth\\n+# X\\n*** End Patch";
    assertThat(
            hook.run(
                0,
                tool("PreToolUse", "ApplyPatch", "{\"input\":\"" + patch + "\"}"),
                "--guard-approved-specs"))
        .isZero();
    assertThat(hook.out.toString()).contains("\"permissionDecision\":\"deny\"");
  }

  @Test
  void theGuardDeniesAProtectedShellCommand() {
    assertThat(
            hook.run(
                0,
                tool("PreToolUse", "Execute", "{\"command\":\"rm openspec/specs/auth/spec.md\"}"),
                "--guard-approved-specs"))
        .isZero();
    assertThat(hook.out.toString()).contains("\"permissionDecision\":\"deny\"");
  }

  @Test
  void theGuardAllowsAnEditOutsideApprovedSpecs() {
    assertThat(
            hook.run(
                0,
                tool("PreToolUse", "Create", "{\"file_path\":\"src/A.java\"}"),
                "--guard-approved-specs"))
        .isZero();
    assertThat(hook.out.toString()).isEmpty();
  }
}
