package dev.ward.domain.provenance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProvenancePolicyTest {
  private final ProvenancePolicy policy = new ProvenancePolicy();

  @Test
  void acceptsOneArchiveThatProducesTheExactLiveSpecTransition() {
    final GitRevisionSnapshot baseline = snapshot("old");
    final String archive = "openspec/changes/archive/add-feature";
    final GitRevisionSnapshot candidate =
        new GitRevisionSnapshot(
            "candidate",
            Map.of(
                "openspec/specs/x/spec.md",
                "new",
                "openspec/changes/archive/existing/proposal.md",
                "immutable",
                archive + "/tasks.md",
                "- [x] done"));
    final GitRevisionSnapshot replay = snapshot("new");

    assertThat(
            policy.evaluate(
                baseline,
                candidate,
                List.of(GitChange.modified("openspec/specs/x/spec.md")),
                Map.of(archive, replay)))
        .isEmpty();
  }

  @Test
  void rejectsUnrelatedArchiveAndRenameRepresentations() {
    final GitRevisionSnapshot baseline = snapshot("old");
    final GitRevisionSnapshot candidate =
        new GitRevisionSnapshot(
            "candidate",
            Map.of(
                "openspec/specs/y/spec.md", "new",
                "openspec/changes/archive/add-feature/tasks.md", "- [x] done"));
    final String archive = "openspec/changes/archive/add-feature";

    assertThat(
            policy.evaluate(
                baseline,
                candidate,
                List.of(GitChange.renamed("openspec/specs/x/spec.md", "openspec/specs/y/spec.md")),
                Map.of(archive, snapshot("old"))))
        .extracting(EnforcementFinding::category)
        .contains(EnforcementFinding.Category.PROVENANCE);
  }

  @Test
  void bootstrapRequiresDeclarationAndAbsentBaseline() {
    final BootstrapPolicy bootstrap = new BootstrapPolicy();
    final GitRevisionSnapshot empty = new GitRevisionSnapshot("base", Map.of());
    final GitRevisionSnapshot candidate = snapshot("new");

    assertThat(bootstrap.check(empty, candidate, new BootstrapDeclaration("approved"), true))
        .isEmpty();
    assertThat(bootstrap.check(empty, candidate, new BootstrapDeclaration(""), true)).isPresent();
    assertThat(
            bootstrap.check(snapshot("old"), candidate, new BootstrapDeclaration("approved"), true))
        .isPresent();
    assertThat(bootstrap.check(empty, candidate, new BootstrapDeclaration(""), false)).isPresent();
  }

  private static GitRevisionSnapshot snapshot(final String content) {
    return new GitRevisionSnapshot(
        content,
        Map.of(
            "openspec/specs/x/spec.md",
            content,
            "openspec/changes/archive/existing/proposal.md",
            "immutable"));
  }
}
