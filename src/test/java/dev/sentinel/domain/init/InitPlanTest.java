package dev.sentinel.domain.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.agent.PlannedFileChange;
import dev.sentinel.domain.agent.PlannedFileChange.Action;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InitPlanTest {
  private static final PlannedFileChange CREATED =
      new PlannedFileChange(Path.of("sentinel.toml"), Action.CREATE);
  private static final PlannedFileChange KEPT =
      new PlannedFileChange(Path.of("pom.xml"), Action.UNCHANGED);

  @Test
  void conflictIsDetected() {
    final InitPlan plan =
        new InitPlan(
            List.of(CREATED, new PlannedFileChange(Path.of("hook"), Action.CONFLICT)),
            Optional.empty());

    assertThat(plan.hasConflict()).isTrue();
  }

  @Test
  void planWithoutConflictIsClean() {
    assertThat(new InitPlan(List.of(CREATED, KEPT), Optional.empty()).hasConflict()).isFalse();
  }

  @Test
  void unchangedFilesAreNotVisible() {
    assertThat(new InitPlan(List.of(CREATED, KEPT), null).visibleFiles()).containsExactly(CREATED);
  }

  @Test
  void onlyCreateModifyAndRemoveWrite() {
    assertThat(List.of(Action.values()).stream().filter(Action::writes))
        .containsExactly(Action.CREATE, Action.MODIFY, Action.REMOVE);
  }
}
