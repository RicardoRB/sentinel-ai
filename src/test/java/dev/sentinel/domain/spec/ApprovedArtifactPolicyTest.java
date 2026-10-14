package dev.sentinel.domain.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ApprovedArtifactPolicyTest {

  @Test
  void approvedSpecsAreProtectedForEveryOperation() {
    for (final GuardOperation operation : GuardOperation.values()) {
      assertThat(
              ApprovedArtifactPolicy.evaluate(
                  Path.of("openspec/specs/agent-integration/spec.md"), operation))
          .contains(ProtectedArea.SPECS);
    }
    assertThat(ApprovedArtifactPolicy.evaluate(Path.of("openspec/specs"), GuardOperation.WRITE))
        .contains(ProtectedArea.SPECS);
  }

  @Test
  void archivedChangesAreProtected() {
    assertThat(
            ApprovedArtifactPolicy.evaluate(
                Path.of("openspec/changes/archive/2026-10-05-old/tasks.md"), GuardOperation.WRITE))
        .contains(ProtectedArea.ARCHIVE);
    assertThat(
            ApprovedArtifactPolicy.evaluate(
                Path.of("openspec/changes/archive/2026-10-13-foo"), GuardOperation.MOVE_TO))
        .contains(ProtectedArea.ARCHIVE);
    assertThat(
            ApprovedArtifactPolicy.evaluate(
                Path.of("openspec/changes/archive/2026-10-05-old"), GuardOperation.MOVE_FROM))
        .contains(ProtectedArea.ARCHIVE);
  }

  @Test
  void activeChangesAndUnrelatedFilesAreAllowed() {
    assertThat(
            ApprovedArtifactPolicy.evaluate(
                Path.of("openspec/changes/foo/specs/agent-integration/spec.md"),
                GuardOperation.WRITE))
        .isEmpty();
    assertThat(
            ApprovedArtifactPolicy.evaluate(Path.of("openspec/changes/foo"), GuardOperation.DELETE))
        .isEmpty();
    assertThat(
            ApprovedArtifactPolicy.evaluate(
                Path.of("openspec/changes/old"), GuardOperation.MOVE_TO))
        .isEmpty();
    assertThat(
            ApprovedArtifactPolicy.evaluate(
                Path.of("src/main/java/App.java"), GuardOperation.WRITE))
        .isEmpty();
    assertThat(ApprovedArtifactPolicy.evaluate(Path.of("openspec/specsx"), GuardOperation.WRITE))
        .isEmpty();
  }

  @Test
  void removingOrMovingAnAncestorIsRefused() {
    assertThat(ApprovedArtifactPolicy.evaluate(Path.of("openspec"), GuardOperation.DELETE))
        .contains(ProtectedArea.ANCESTOR);
    assertThat(
            ApprovedArtifactPolicy.evaluate(Path.of("openspec/changes"), GuardOperation.MOVE_FROM))
        .contains(ProtectedArea.ANCESTOR);
    assertThat(ApprovedArtifactPolicy.evaluate(Path.of(""), GuardOperation.DELETE))
        .contains(ProtectedArea.ANCESTOR);
    assertThat(ApprovedArtifactPolicy.evaluate(Path.of("openspec"), GuardOperation.WRITE))
        .isEmpty();
    assertThat(ApprovedArtifactPolicy.evaluate(Path.of("openspec"), GuardOperation.MOVE_TO))
        .isEmpty();
  }

  @Test
  void escapingAbsoluteAndMissingPathsAreNotThisPolicysConcern() {
    assertThat(ApprovedArtifactPolicy.evaluate(Path.of("../openspec/specs"), GuardOperation.WRITE))
        .isEmpty();
    assertThat(ApprovedArtifactPolicy.evaluate(Path.of("/openspec/specs"), GuardOperation.WRITE))
        .isEmpty();
    assertThat(ApprovedArtifactPolicy.evaluate(null, GuardOperation.WRITE)).isEmpty();
    assertThat(
            ApprovedArtifactPolicy.evaluate(
                Path.of("openspec/changes/foo/../../specs/x/spec.md"), GuardOperation.WRITE))
        .contains(ProtectedArea.SPECS);
  }

  @Test
  void deltaPathMirrorsTheCapabilityPath() {
    assertThat(
            ApprovedArtifactPolicy.deltaPath(Path.of("openspec/specs/identity/user-auth/spec.md")))
        .isEqualTo("openspec/changes/<change>/specs/identity/user-auth/spec.md");
    assertThat(ApprovedArtifactPolicy.deltaPath(Path.of("openspec/specs/x/notes.md")))
        .isEqualTo("openspec/changes/<change>/specs/x/spec.md");
    assertThat(ApprovedArtifactPolicy.deltaPath(Path.of("openspec/specs/x")))
        .isEqualTo("openspec/changes/<change>/specs/x/spec.md");
    assertThat(ApprovedArtifactPolicy.deltaPath(Path.of("openspec/specs")))
        .isEqualTo("openspec/changes/<change>/specs/<capability-path>/spec.md");
  }

  @Test
  void refusalsDescribeTheirArea() {
    assertThat(GuardRefusal.forPath(ProtectedArea.SPECS, Path.of("openspec/specs/x/spec.md")))
        .isEqualTo(
            new GuardRefusal(
                GuardRefusal.Reason.APPROVED_SPEC,
                "openspec/specs/x/spec.md",
                "openspec/changes/<change>/specs/x/spec.md"));
    assertThat(GuardRefusal.forPath(ProtectedArea.ARCHIVE, Path.of("openspec/changes/archive/a")))
        .isEqualTo(
            GuardRefusal.of(GuardRefusal.Reason.ARCHIVED_CHANGE, "openspec/changes/archive/a"));
    assertThat(GuardRefusal.forPath(ProtectedArea.ANCESTOR, Path.of("")).subject()).isEqualTo(".");
    assertThat(GuardOperation.MOVE_FROM.removesTree()).isTrue();
    assertThat(GuardOperation.MOVE_TO.removesTree()).isFalse();
  }
}
