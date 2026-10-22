package dev.ward.application.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FixedProjectInspection;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A nested project's turn-end check runs only when the turn changed the project. */
class TurnEndScopeTest {
  private static final Path REPOSITORY = Path.of("/repo");
  private static final Project API =
      new Project(
          REPOSITORY.resolve("apps/api"),
          Language.JAVA,
          BuildTool.MAVEN,
          Framework.NONE,
          false,
          false,
          REPOSITORY);
  private static final Project ROOT =
      new Project(REPOSITORY, Language.JAVA, BuildTool.MAVEN, Framework.NONE);

  private final List<Path> asked = new ArrayList<>();

  @Test
  void unchangedNestedProjectSkipsTheTurnEndCheck() {
    assertThat(scope(API, false).applies(API.root())).isFalse();
    assertThat(asked).containsExactly(API.root());
  }

  @Test
  void changedNestedProjectRunsTheTurnEndCheck() {
    assertThat(scope(API, true).applies(API.root())).isTrue();
  }

  @Test
  void rootProjectsNeverAskVersionControl() {
    assertThat(scope(ROOT, false).applies(REPOSITORY)).isTrue();
    assertThat(asked).isEmpty();
    assertThat(TurnEndScope.ALWAYS.applies(REPOSITORY)).isTrue();
  }

  private TurnEndScope scope(final Project project, final boolean changed) {
    return new TurnEndScope(
        new ProjectDetector(new FixedProjectInspection(project)),
        directory -> {
          asked.add(directory);
          return changed;
        });
  }
}
