package dev.sentinel.domain.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GuardFeedbackTest {

  @Test
  void refusedSpecEditPointsToTheDeltaAndActiveChanges() {
    final String reason =
        GuardFeedback.render(
            List.of(
                GuardRefusal.forPath(
                    ProtectedArea.SPECS, Path.of("openspec/specs/identity/user-auth/spec.md"))),
            List.of("bar", "foo"));

    assertThat(reason)
        .isEqualTo(
            """
            Sentinel blocked this tool call: approved OpenSpec artifacts are read-only for agents.
            - openspec/specs/identity/user-auth/spec.md is an approved spec. Record the change as \
            a delta in an active change: openspec/changes/<change>/specs/identity/user-auth/spec.md
            Active changes: bar, foo
            Archiving a change or syncing deltas into openspec/specs/ requires a human. Ask the \
            user to run `openspec archive <change>` (or the sync workflow) themselves.""");
  }

  @Test
  void withoutActiveChangesItExplainsHowToCreateOne() {
    assertThat(
            GuardFeedback.render(
                List.of(
                    GuardRefusal.forPath(ProtectedArea.SPECS, Path.of("openspec/specs/x/spec.md"))),
                List.of()))
        .contains("No active change exists. Create one with `openspec new change <name>`");
  }

  @Test
  void archiveCommandsAndSkillsAskForAHuman() {
    final String reason =
        GuardFeedback.render(
            List.of(
                GuardRefusal.of(GuardRefusal.Reason.ARCHIVE_COMMAND, "shell command"),
                GuardRefusal.of(GuardRefusal.Reason.ARCHIVE_OR_SYNC_SKILL, "opsx:archive"),
                GuardRefusal.of(GuardRefusal.Reason.UNINSPECTABLE, "patch"),
                GuardRefusal.forPath(ProtectedArea.ARCHIVE, Path.of("openspec/changes/archive/a")),
                GuardRefusal.forPath(ProtectedArea.ANCESTOR, Path.of("openspec"))),
            List.of("foo"));

    assertThat(reason)
        .contains("`openspec archive` archives a change and updates approved specs. Ask the user")
        .contains("The skill opsx:archive archives or syncs into approved specs.")
        .contains("Sentinel could not inspect the patch")
        .contains("openspec/changes/archive/a is part of an archived change")
        .contains("openspec contains approved specs or archived changes")
        .contains("requires a human");
  }

  @Test
  void listsAreBoundedAndDuplicatesCollapse() {
    final List<String> changes = IntStream.range(0, 12).mapToObj(i -> "change-" + i).toList();
    final List<GuardRefusal> refusals =
        IntStream.range(0, 25)
            .mapToObj(
                i -> GuardRefusal.forPath(ProtectedArea.SPECS, Path.of("openspec/specs/c" + i)))
            .toList();

    final String reason = GuardFeedback.render(refusals, changes);

    assertThat(reason)
        .contains("change-9 (+2 more)")
        .doesNotContain("change-10")
        .contains("- 5 more refused targets omitted");
    assertThat(
            GuardFeedback.render(
                    Collections.nCopies(
                        3, GuardRefusal.of(GuardRefusal.Reason.UNINSPECTABLE, "patch")),
                    List.of("a"))
                .lines()
                .filter(line -> line.contains("could not inspect"))
                .count())
        .isEqualTo(1);
  }

  @Test
  void outputNeverExceedsTheLimit() {
    final String longName = "x".repeat(300);
    final List<GuardRefusal> refusals =
        IntStream.range(0, 20)
            .mapToObj(
                i ->
                    GuardRefusal.forPath(
                        ProtectedArea.SPECS, Path.of("openspec/specs/" + longName + i)))
            .toList();

    final String reason = GuardFeedback.render(refusals, List.of());

    assertThat(reason).hasSize(GuardFeedback.MAX_LENGTH).endsWith("… output truncated");
  }

  @Test
  void skillPolicyRecognizesArchiveAndSyncSkillsOnly() {
    assertThat(SkillPolicy.archivesOrSyncs("opsx:archive")).isTrue();
    assertThat(SkillPolicy.archivesOrSyncs("opsx-sync")).isTrue();
    assertThat(SkillPolicy.archivesOrSyncs("openspec-sync-specs")).isTrue();
    assertThat(SkillPolicy.archivesOrSyncs("openspec-archive-change")).isTrue();
    assertThat(SkillPolicy.archivesOrSyncs("my-plugin:openspec-bulk-archive-change")).isTrue();
    assertThat(SkillPolicy.archivesOrSyncs(":openspec-sync-openspec")).isTrue();
    assertThat(SkillPolicy.archivesOrSyncs("opsx:propose")).isFalse();
    assertThat(SkillPolicy.archivesOrSyncs("openspec-apply-change")).isFalse();
    assertThat(SkillPolicy.archivesOrSyncs("archive-photos")).isFalse();
    assertThat(SkillPolicy.archivesOrSyncs(" ")).isFalse();
    assertThat(SkillPolicy.archivesOrSyncs(null)).isFalse();
  }
}
