package dev.sentinel.domain.spec;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.AgentPayloadFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class PatchTargetsTest {

  private static List<GuardTarget> targets(final String patch) {
    return PatchTargets.parse(patch).orElseThrow();
  }

  private static String fixturePatch(final String fixture) {
    return AgentPayloadFixtures.json(fixture).get("args").get("patchText").asString();
  }

  @Test
  void everyFileInARecordedMultiFilePatchIsATarget() {
    assertThat(targets(fixturePatch("opencode/apply_patch-multi-file.json")))
        .containsExactly(
            GuardTarget.of(GuardOperation.WRITE, "openspec/changes/foo/proposal.md"),
            GuardTarget.of(GuardOperation.WRITE, "openspec/specs/x/spec.md"));
  }

  @Test
  void addUpdateAndDeleteHeadersMapToOperations() {
    assertThat(targets(fixturePatch("opencode/apply_patch-active-change.json")))
        .containsExactly(
            GuardTarget.of(GuardOperation.WRITE, "openspec/changes/foo/design.md"),
            GuardTarget.of(GuardOperation.WRITE, "openspec/changes/foo/tasks.md"),
            GuardTarget.of(GuardOperation.DELETE, "openspec/changes/foo/notes.md"));
  }

  @Test
  void moveDestinationAndSourceAreBothTargets() {
    assertThat(
            targets(
                """
                *** Begin Patch
                *** Update File: openspec/changes/foo/specs/x/spec.md
                *** Move to: openspec/specs/x/spec.md
                @@
                -a
                +b
                *** End Patch
                """))
        .containsExactly(
            GuardTarget.of(GuardOperation.WRITE, "openspec/changes/foo/specs/x/spec.md"),
            GuardTarget.of(GuardOperation.MOVE_FROM, "openspec/changes/foo/specs/x/spec.md"),
            GuardTarget.of(GuardOperation.MOVE_TO, "openspec/specs/x/spec.md"));
  }

  @Test
  void unifiedAndGitDiffHeadersAreUnderstood() {
    assertThat(
            targets(
                """
                diff --git a/openspec/specs/x/spec.md b/openspec/specs/x/spec.md
                --- a/openspec/specs/x/spec.md\t2026-10-13 10:00:00
                +++ b/openspec/specs/x/spec.md\t2026-10-13 10:01:00
                @@ -1 +1 @@
                --- removed line that looks like a header
                --- /dev/null
                +++ b/openspec/changes/foo/new.md
                --- "a/openspec/changes/foo/old.md"
                +++ /dev/null
                --- a/one.md
                +++ b/two.md
                rename from openspec/changes/foo
                rename to openspec/changes/archive/2026-10-13-foo
                copy to openspec/specs/y/spec.md
                """))
        .containsExactly(
            GuardTarget.of(GuardOperation.WRITE, "openspec/specs/x/spec.md"),
            GuardTarget.of(GuardOperation.WRITE, "openspec/changes/foo/new.md"),
            GuardTarget.of(GuardOperation.DELETE, "openspec/changes/foo/old.md"),
            GuardTarget.of(GuardOperation.MOVE_FROM, "one.md"),
            GuardTarget.of(GuardOperation.MOVE_TO, "two.md"),
            GuardTarget.of(GuardOperation.MOVE_FROM, "openspec/changes/foo"),
            GuardTarget.of(GuardOperation.MOVE_TO, "openspec/changes/archive/2026-10-13-foo"),
            GuardTarget.of(GuardOperation.WRITE, "openspec/specs/y/spec.md"));
  }

  @Test
  void unrecognizedTextIsUninspectableAndEmptyTextHasNoTargets() {
    assertThat(PatchTargets.parse("please change openspec/specs/x/spec.md")).isEmpty();
    assertThat(PatchTargets.parse("--- only an old header")).isEmpty();
    assertThat(PatchTargets.parse("  \n")).contains(List.of());
    assertThat(PatchTargets.parse(null)).contains(List.of());
    assertThat(targets("*** Begin Patch\n*** Add File:   \n*** End Patch")).isEmpty();
  }
}
