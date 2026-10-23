package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Only the project directory's status counts, and an unknown status counts as changed. */
class GitProjectChangesTest {
  private static final int GIT_FAILURE = 128;
  private static final Path API = Path.of("/repo/apps/api");

  @Test
  void statusIsLimitedToTheProjectDirectory() {
    final FakeCommandExecutor git = new FakeCommandExecutor(0, "", "");

    assertThat(new GitProjectChanges(git).changedBelow(API)).isFalse();
    assertThat(git.commands).containsExactly(List.of("git", "status", "--porcelain", "--", "."));
    assertThat(git.workingDirectories).containsExactly(API);
  }

  @Test
  void reportedChangesCount() {
    assertThat(
            new GitProjectChanges(new FakeCommandExecutor(0, " M pom.xml\n", "")).changedBelow(API))
        .isTrue();
  }

  @Test
  void failingGitCountsAsChanged() {
    assertThat(
            new GitProjectChanges(new FakeCommandExecutor(GIT_FAILURE, "", "fatal"))
                .changedBelow(API))
        .isTrue();
  }
}
