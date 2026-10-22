package dev.ward.infrastructure.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WardCliSpecGuardTest extends WardCliSpecSupport {
  static final String GUARD = "guard";
  static final String APPROVED = "openspec/specs/x/spec.md";

  @BeforeEach
  void openSpecTree() throws IOException {
    createOpenSpecTree(project);
  }

  /** Creates an active change, an archived change, and the approved spec {@link #APPROVED}. */
  static void createOpenSpecTree(final Path project) throws IOException {
    Files.createDirectories(project.resolve("openspec/changes/foo"));
    Files.createDirectories(project.resolve("openspec/changes/archive/2026-10-05-old"));
    Files.createDirectories(project.resolve("openspec/specs/x"));
    Files.writeString(project.resolve(APPROVED), "# x");
  }

  @Test
  void allowedWritePrintsNothing() {
    assertThat(
            run(SPEC, GUARD, DIRECTORY, project.toString(), "--", "openspec/changes/foo/design.md"))
        .isEqualTo(ExitCodes.OK);
    assertThat(out.toString()).isEmpty();
  }

  @Test
  void refusedWritePrintsTheReason() {
    assertThat(
            run(SPEC, GUARD, DIRECTORY, project.toString(), project.resolve(APPROVED).toString()))
        .isEqualTo(ExitCodes.FAILED);
    assertThat(out.toString())
        .startsWith("Ward blocked this tool call")
        .contains("openspec/changes/<change>/specs/x/spec.md")
        .contains("Active changes: foo");
  }

  @Test
  void skillsAreChecked() {
    assertThat(run(SPEC, GUARD, DIRECTORY, project.toString(), "--skill", "opsx:archive"))
        .isEqualTo(ExitCodes.FAILED);
    assertThat(run(SPEC, GUARD, DIRECTORY, project.toString(), "--skill", "opsx:apply"))
        .isEqualTo(ExitCodes.OK);
  }

  @Test
  void conflictingModesAreAUsageError() {
    assertThat(run(SPEC, GUARD, DIRECTORY, project.toString(), "--skill", "x", "openspec/a.md"))
        .isEqualTo(ExitCodes.ERROR);
    assertThat(err.toString()).contains("Use only one of");
  }

  @Test
  void missingProjectIsAnError(@TempDir final Path empty) {
    assertThat(run(SPEC, GUARD, DIRECTORY, empty.toString(), APPROVED)).isEqualTo(ExitCodes.ERROR);
    assertThat(out.toString()).isEmpty();
    assertThat(err.toString()).isNotEmpty();
  }
}
