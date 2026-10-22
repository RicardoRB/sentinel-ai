package dev.ward.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.cli.ExitCodes;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClaudeCodeHookPayloadTest extends ClaudeCodeHookTestSupport {
  @Test
  void withoutTheStdinFlagThePayloadIsNotRead() {
    assertThat(run(failingInput(), false, "--exclude-spec", "-C", root.toString()))
        .isEqualTo(ExitCodes.OK);
    assertThat(commands).containsExactly(List.of("mvn", "test"));
  }

  @Test
  void withoutExcludeSpecTheQualityCheckStillRunsSpecGates() {
    assertThat(run(failingInput(), false, "-C", root.toString())).isEqualTo(ExitCodes.OK);
    assertThat(commands)
        .containsExactly(
            List.of("mvn", "test"),
            List.of("openspec", "validate", "--all", "--strict", "--no-interactive"));
  }

  @Test
  void interactiveStdinIsNeverRead() {
    assertThat(run(failingInput(), true, "--event-stdin", "--exclude-spec", "-C", root.toString()))
        .isEqualTo(ExitCodes.OK);
    assertThat(commands).containsExactly(List.of("mvn", "test"));
  }

  @Test
  void emptyOrMalformedPayloadRunsOnlyTheQualityCheck() {
    assertThat(hook("")).isEqualTo(ExitCodes.OK);
    assertThat(hook("not json")).isEqualTo(ExitCodes.OK);
    assertThat(hook("[1, 2]")).isEqualTo(ExitCodes.OK);
    assertThat(commands).containsOnly(List.of("mvn", "test"));
    assertThat(out.toString()).isEmpty();
  }

  @Test
  void realisticPayloadWithUnknownFieldsRoutesTheEditedFile() {
    specsValid = false;

    final int exit =
        hook(
            """
            {"session_id":"abc","transcript_path":"/t.jsonl","cwd":"%s","permission_mode":"default",
             "hook_event_name":"PostToolUse","tool_name":"Write",
             "tool_input":{"file_path":"%s","content":"# P\\n","replace_all":false,"edits":[{"a":1}]},
             "tool_response":{"filePath":"x","success":true},"tool_use_id":"t1"}
            """
                .formatted(root, root.resolve("openspec/changes/foo/proposal.md")));

    assertThat(exit).isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString())
        .contains(
            "Ward spec validation found problems:",
            "openspec change foo: 1 error",
            "ERROR x/spec.md: must include at least one scenario");
    assertThat(err.toString()).doesNotContain("tests:");
    assertThat(out.toString()).isEmpty();
  }
}
