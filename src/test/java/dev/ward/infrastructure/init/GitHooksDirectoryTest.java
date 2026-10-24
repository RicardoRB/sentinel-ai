package dev.ward.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Ward installs hooks where Git runs them, falling back to the repository's .git/hooks. */
class GitHooksDirectoryTest {
  private static final Path REPOSITORY = Path.of("/work/acme-shop");
  private static final Project API =
      new Project(
          REPOSITORY.resolve("apps/api"),
          Language.JAVA,
          BuildTool.MAVEN,
          Framework.NONE,
          false,
          false,
          REPOSITORY,
          Set.of());

  @Test
  void relativeAnswerResolvesAgainstTheProjectRoot() {
    final FakeCommandExecutor git = new FakeCommandExecutor(0, "../../.git/hooks\n", "");

    assertThat(new GitHooksDirectory(git).resolve(API)).isEqualTo(REPOSITORY.resolve(".git/hooks"));
    assertThat(git.commands).containsExactly(List.of("git", "rev-parse", "--git-path", "hooks"));
    assertThat(git.workingDirectories).containsExactly(API.root());
  }

  @Test
  void configuredHooksPathIsUsed() {
    final FakeCommandExecutor git = new FakeCommandExecutor(0, "/work/acme-shop/.githooks\n", "");

    assertThat(new GitHooksDirectory(git).resolve(API)).isEqualTo(REPOSITORY.resolve(".githooks"));
  }

  @Test
  void failingGitFallsBackToTheRepositoryRoot() {
    final FakeCommandExecutor git = new FakeCommandExecutor(128, "", "not a git repository");

    assertThat(new GitHooksDirectory(git).resolve(API)).isEqualTo(REPOSITORY.resolve(".git/hooks"));
  }

  @Test
  void emptyAnswerFallsBackToTheRepositoryRoot() {
    final FakeCommandExecutor git = new FakeCommandExecutor(0, "  \n", "");

    assertThat(new GitHooksDirectory(git).resolve(API)).isEqualTo(REPOSITORY.resolve(".git/hooks"));
  }
}
