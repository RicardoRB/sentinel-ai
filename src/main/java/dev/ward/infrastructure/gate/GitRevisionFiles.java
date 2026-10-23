package dev.ward.infrastructure.gate;

import dev.ward.domain.gate.RevisionFiles;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;

/** Resolves revisions with fixed Git argument vectors; never fetches or writes. */
public final class GitRevisionFiles implements RevisionFiles {
  private final CommandExecutor executor;

  /** Creates the adapter; {@code executor} runs Git. */
  @Inject
  public GitRevisionFiles(final CommandExecutor executor) {
    this.executor = executor;
  }

  @Override
  public Optional<String> resolve(final Path root, final String revision) {
    return commit(
        root,
        List.of(
            "git", "rev-parse", "--verify", "--quiet", "--end-of-options", revision + "^{commit}"));
  }

  @Override
  public Optional<String> mergeBase(final Path root, final String first, final String second) {
    return commit(root, List.of("git", "merge-base", "--end-of-options", first, second));
  }

  // ./ makes Git resolve the path from the project root, which may be below the repository root.
  @Override
  public boolean exists(final Path root, final String commit, final String path) {
    return executor
        .execute(List.of("git", "cat-file", "-e", commit + ":./" + path), root)
        .succeeded();
  }

  private Optional<String> commit(final Path root, final List<String> command) {
    final CommandResult result = executor.execute(command, root);
    final String output = result.stdout().trim();
    return result.succeeded() && !output.isEmpty() ? Optional.of(output) : Optional.empty();
  }
}
