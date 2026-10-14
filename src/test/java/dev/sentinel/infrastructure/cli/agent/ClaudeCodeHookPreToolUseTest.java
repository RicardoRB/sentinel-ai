package dev.sentinel.infrastructure.cli.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.JsonTree;
import dev.sentinel.infrastructure.cli.ExitCodes;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClaudeCodeHookPreToolUseTest extends ClaudeCodeHookTestSupport {
  private static final String GUARD = "--guard-approved-specs";
  private static final String EVENT_STDIN = "--event-stdin";

  @BeforeEach
  void approvedArtifacts() throws IOException {
    Files.createDirectories(root.resolve("openspec/specs/x"));
    Files.writeString(root.resolve("openspec/specs/x/spec.md"), "# x");
    Files.createDirectories(root.resolve("openspec/changes/archive/2026-10-05-old"));
  }

  private int guard(final String payload) {
    out.getBuffer().setLength(0);
    return run(
        new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)),
        false,
        GUARD,
        EVENT_STDIN,
        "-C",
        root.toString());
  }

  private int guardFixture(final String name) {
    return guard(ClaudeHookGuardPayloadTest.fixture(name, root));
  }

  private JsonTree decision() {
    return JsonTree.parse(out.toString()).get("hookSpecificOutput");
  }

  @Test
  void writeToAnApprovedSpecIsDenied() {
    assertThat(guardFixture("pre-tool-write-approved-spec.json")).isEqualTo(ExitCodes.OK);

    final JsonTree decision = decision();
    assertThat(JsonTree.parse(out.toString()).size()).isEqualTo(1);
    assertThat(decision.size()).isEqualTo(3);
    assertThat(decision.get("hookEventName").asString()).isEqualTo("PreToolUse");
    assertThat(decision.get("permissionDecision").asString()).isEqualTo("deny");
    assertThat(decision.get("permissionDecisionReason").asString())
        .contains("openspec/specs/x/spec.md is an approved spec")
        .contains("openspec/changes/<change>/specs/x/spec.md")
        .contains("Active changes: foo");
    assertThat(commands).isEmpty();
  }

  @Test
  void archiveCommandSkillArchiveEditAndNotebookAreDenied() {
    for (final String fixture :
        new String[] {
          "pre-tool-bash-archive.json",
          "pre-tool-skill-archive.json",
          "pre-tool-multiedit-archive.json",
          "pre-tool-notebook-edit.json"
        }) {
      assertThat(guardFixture(fixture)).as(fixture).isEqualTo(ExitCodes.OK);
      assertThat(decision().get("permissionDecision").asString()).as(fixture).isEqualTo("deny");
    }
    assertThat(commands).isEmpty();
  }

  @Test
  void allowedCallsPrintNothingAndNeverGrantPermission() {
    for (final String fixture :
        new String[] {
          "pre-tool-edit-active-change.json",
          "pre-tool-bash-read.json",
          "pre-tool-skill-propose.json"
        }) {
      assertThat(guardFixture(fixture)).as(fixture).isEqualTo(ExitCodes.OK);
      assertThat(out.toString()).as(fixture).isEmpty();
    }
    assertThat(err.toString()).isEmpty();
    assertThat(commands).isEmpty();
  }

  @Test
  void malformedPayloadIsDenied() {
    assertThat(guard("not json")).isEqualTo(ExitCodes.OK);

    assertThat(decision().get("permissionDecision").asString()).isEqualTo("deny");
    assertThat(decision().get("permissionDecisionReason").asString())
        .contains("could not inspect the tool payload");
  }

  @Test
  void otherEventsAndMissingPayloadsDecideNothing() {
    assertThat(guardFixture("stop.json")).isEqualTo(ExitCodes.OK);
    assertThat(guard(postToolUse(root.resolve("openspec/specs/x/spec.md").toString())))
        .isEqualTo(ExitCodes.OK);
    assertThat(run(failingInput(), true, GUARD, EVENT_STDIN, "-C", root.toString()))
        .isEqualTo(ExitCodes.OK);
    assertThat(run(failingInput(), false, GUARD, "-C", root.toString())).isEqualTo(ExitCodes.OK);

    assertThat(out.toString()).isEmpty();
    assertThat(commands).isEmpty();
  }

  @Test
  void sentinelErrorsAreReportedWithoutBlocking(@TempDir final Path empty) {
    final String payload =
        ClaudeHookGuardPayloadTest.fixture("pre-tool-write-approved-spec.json", root);

    assertThat(
            run(
                new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)),
                false,
                GUARD,
                EVENT_STDIN,
                "-C",
                empty.toString()))
        .isEqualTo(ExitCodes.FAILED);
    assertThat(out.toString()).isEmpty();
    assertThat(err.toString()).startsWith("sentinel: ");
  }
}
