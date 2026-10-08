package dev.sentinel.infrastructure.loop;

import dev.sentinel.domain.loop.GitState;
import dev.sentinel.domain.loop.GitStateInspection;
import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.process.CommandResult;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;

public final class GitStateInspector implements GitStateInspection {
  private final CommandExecutor executor;

  @Inject
  public GitStateInspector(final CommandExecutor executor) {
    this.executor = executor;
  }

  @Override
  public GitState inspect(final Path root) {
    final CommandResult repository =
        executor.execute(List.of("git", "rev-parse", "--is-inside-work-tree"), root);
    if (!repository.succeeded()) {
      return new GitState(false, "", false, "Not a Git repository.");
    }
    final CommandResult branch = executor.execute(List.of("git", "branch", "--show-current"), root);
    final CommandResult status = executor.execute(List.of("git", "status", "--porcelain"), root);
    return new GitState(
        true,
        branch.stdout().trim(),
        !status.stdout().isBlank(),
        status.stdout().isBlank()
            ? "Working tree clean."
            : "Working tree has uncommitted changes.");
  }
}
