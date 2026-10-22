package dev.ward.application.spec;

import static dev.ward.ProjectFixtures.PLAIN_POM;
import static dev.ward.ProjectFixtures.withPom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.application.project.ProjectDetector;
import dev.ward.application.project.ProjectNotFoundException;
import dev.ward.domain.spec.GuardDecision;
import dev.ward.domain.spec.GuardRequest;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import dev.ward.infrastructure.spec.FileSystemProjectPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApprovedArtifactGuardServiceTest {
  static final String SPEC = "openspec/specs/x/spec.md";
  private static final String ENABLED_OPENSPEC_GATE =
      """
      version = 1
      [quality-gates.openspec]
      command = ["./missing-openspec", "validate", "--all"]
      """;

  @TempDir Path root;

  /** Creates a project with an active change, an archived change, and an approved spec. */
  static void openSpecProject(final Path root) throws IOException {
    withPom(root, PLAIN_POM);
    Files.createDirectories(root.resolve("openspec/changes/foo"));
    Files.createDirectories(root.resolve("openspec/changes/archive/2026-10-05-old"));
    Files.createDirectories(root.resolve("openspec/specs/x"));
    Files.writeString(root.resolve(SPEC), "# x");
  }

  static ApprovedArtifactGuardService service() {
    return new ApprovedArtifactGuardService(
        new ProjectDetector(new FileSystemProjectInspection()), new FileSystemProjectPaths());
  }

  @BeforeEach
  void project() throws IOException {
    openSpecProject(root);
  }

  private GuardDecision paths(final String... paths) {
    return service().evaluate(GuardRequest.paths(root, "", List.of(paths)));
  }

  @Test
  void writesToApprovedSpecsAreRefusedWithActionableFeedback() {
    final GuardDecision decision = paths(root.resolve(SPEC).toString());

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason())
        .contains(SPEC + " is an approved spec")
        .contains("openspec/changes/<change>/specs/x/spec.md")
        .contains("Active changes: foo")
        .contains("requires a human");
  }

  @Test
  void activeChangesAndUnrelatedFilesAreAllowed() {
    assertThat(paths("openspec/changes/foo/design.md", "src/App.java"))
        .isEqualTo(GuardDecision.ALLOWED);
    assertThat(paths("openspec/changes/bar/proposal.md")).isEqualTo(GuardDecision.ALLOWED);
  }

  @Test
  void guardIgnoresValidationConfigurationAndLaunchesNothing() throws IOException {
    Files.writeString(root.resolve("ward.toml"), ENABLED_OPENSPEC_GATE);

    assertThat(paths(SPEC).allowed()).isFalse();
    assertThat(root.resolve("missing-openspec")).doesNotExist();
  }

  @Test
  void projectWithoutOpenSpecAllowsEverything(@TempDir final Path bare) {
    withPom(bare, PLAIN_POM);
    final ApprovedArtifactGuardService service = service();

    assertThat(service.evaluate(GuardRequest.paths(bare, "", List.of(SPEC))))
        .isEqualTo(GuardDecision.ALLOWED);
    assertThat(service.evaluate(GuardRequest.shell(bare, "", "openspec archive foo")))
        .isEqualTo(GuardDecision.ALLOWED);
    assertThat(service.evaluate(GuardRequest.unreadable(bare, "tool payload")))
        .isEqualTo(GuardDecision.ALLOWED);
  }

  @Test
  void missingProjectIsReported(@TempDir final Path empty) {
    assertThatThrownBy(() -> service().evaluate(GuardRequest.paths(empty, "", List.of(SPEC))))
        .isInstanceOf(ProjectNotFoundException.class);
  }

  @Test
  void patchesAreRefusedWhenAnyTargetIsProtected() {
    final String patch =
        """
        *** Begin Patch
        *** Update File: openspec/changes/foo/proposal.md
        @@
        -a
        +b
        *** Delete File: openspec/specs/x/spec.md
        *** End Patch
        """;

    final GuardDecision decision = service().evaluate(GuardRequest.patch(root, "", patch));

    assertThat(decision.reason()).contains(SPEC).doesNotContain("proposal.md");
    assertThat(service().evaluate(GuardRequest.patch(root, "", "not a patch")).reason())
        .contains("could not inspect the patch");
  }
}
