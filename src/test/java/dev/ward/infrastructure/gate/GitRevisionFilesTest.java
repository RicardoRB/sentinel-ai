package dev.ward.infrastructure.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitRevisionFilesTest {
  private static final Path ROOT = Path.of("/repo");
  private static final String SHA = "121f86a4671e9aa2233a5f3fc7c91f40935996bf";
  private static final int GIT_FATAL = 128;

  @Test
  void resolvesRevisionToCommit() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, SHA + "\n", "");

    assertThat(new GitRevisionFiles(executor).resolve(ROOT, "origin/main")).contains(SHA);
    assertThat(executor.commands.getFirst())
        .containsExactly(
            "git", "rev-parse", "--verify", "--quiet", "--end-of-options", "origin/main^{commit}");
    assertThat(executor.workingDirectories).containsExactly(ROOT);
  }

  @Test
  void missingRefOrParentResolvesToEmpty() {
    final GitRevisionFiles files = new GitRevisionFiles(new FakeCommandExecutor(1, "", ""));

    assertThat(files.resolve(ROOT, "origin/nope")).isEmpty();
    assertThat(files.resolve(ROOT, SHA + "^1")).isEmpty();
  }

  @Test
  void findsMergeBase() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, SHA + "\n", "");

    assertThat(new GitRevisionFiles(executor).mergeBase(ROOT, "origin/main", "HEAD")).contains(SHA);
    assertThat(executor.commands.getFirst())
        .containsExactly("git", "merge-base", "--end-of-options", "origin/main", "HEAD");
  }

  @Test
  void missingMergeBaseIsEmpty() {
    final GitRevisionFiles files = new GitRevisionFiles(new FakeCommandExecutor(1, "", ""));

    assertThat(files.mergeBase(ROOT, "origin/main", "HEAD")).isEmpty();
  }

  @Test
  void reportsWhetherFileExistsAtCommit() {
    final List<List<String>> commands = new ArrayList<>();
    final CommandExecutor executor =
        (command, dir) -> {
          commands.add(command);
          final int exit = command.getLast().endsWith("openapi.yml") ? 0 : GIT_FATAL;
          return new CommandResult(exit, "", "", Duration.ZERO);
        };
    final GitRevisionFiles files = new GitRevisionFiles(executor);

    assertThat(files.exists(ROOT, SHA, "openapi.yml")).isTrue();
    assertThat(files.exists(ROOT, SHA, "missing.yml")).isFalse();
    assertThat(commands.getFirst())
        .containsExactly("git", "cat-file", "-e", SHA + ":./openapi.yml");
  }
}
