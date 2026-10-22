package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.agent.PlannedFileChange.Action;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArchitectureAndHookPlanTest {
  private static final QualityPreset.ArchitectureExtras NO_EXTRAS =
      new QualityPreset.ArchitectureExtras(false, false, false);

  private final ArchitectureTestGenerator architecture = new ArchitectureTestGenerator();
  private final QualityHookInstaller hooks = new QualityHookInstaller();

  @Test
  void absentArchitectureTestIsCreated(@TempDir final Path root) {
    final PlannedFileChange plan = architecture.plan(project(root), "layered", NO_EXTRAS);

    assertThat(plan.action()).isEqualTo(Action.CREATE);
    assertThat(plan.file().getFileName()).hasToString("ArchitectureTest.java");
    assertThat(root.resolve("src")).doesNotExist();
  }

  @Test
  void existingArchitectureTestIsKept(@TempDir final Path root) {
    final Path created = architecture.apply(project(root), "layered", NO_EXTRAS).file();

    final PlannedFileChange plan = architecture.plan(project(root), "clean", NO_EXTRAS);

    assertThat(plan.file()).isEqualTo(created);
    assertThat(plan.action()).isEqualTo(Action.UNCHANGED);
  }

  @Test
  void missingHooksAreCreatedWithoutTouchingGit(@TempDir final Path root) {
    assertThat(hooks.plan(root))
        .extracting(PlannedFileChange::action)
        .containsExactly(Action.CREATE, Action.CREATE);
    assertThat(root.resolve(".git")).doesNotExist();
  }

  @Test
  void userHookIsModifiedAndCurrentHookIsUnchanged(@TempDir final Path root) throws IOException {
    final Path preCommit = root.resolve(".git/hooks/pre-commit");
    Files.createDirectories(preCommit.getParent());
    Files.writeString(preCommit, "#!/bin/sh\necho user\n");
    final String original = Files.readString(preCommit);

    final List<PlannedFileChange> before = hooks.plan(root);

    assertThat(before).extracting(PlannedFileChange::action).first().isEqualTo(Action.MODIFY);
    assertThat(Files.readString(preCommit)).isEqualTo(original);
    hooks.apply(root);
    assertThat(hooks.plan(root))
        .extracting(PlannedFileChange::action)
        .containsExactly(Action.UNCHANGED, Action.UNCHANGED);
  }

  @Test
  void malformedMarkerIsAConflict(@TempDir final Path root) throws IOException {
    final Path prePush = root.resolve(".git/hooks/pre-push");
    Files.createDirectories(prePush.getParent());
    Files.writeString(prePush, QualityHookInstaller.START_MARKER + "\n");

    assertThat(hooks.plan(root))
        .extracting(PlannedFileChange::action)
        .containsExactly(Action.CREATE, Action.CONFLICT);
    assertThat(Files.readString(prePush)).isEqualTo(QualityHookInstaller.START_MARKER + "\n");
  }

  private static Project project(final Path root) {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }
}
