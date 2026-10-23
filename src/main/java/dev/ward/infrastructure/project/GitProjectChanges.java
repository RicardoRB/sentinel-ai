package dev.ward.infrastructure.project;

import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.project.ProjectChanges;
import java.nio.file.Path;
import java.util.List;
import javax.inject.Inject;

/** Asks {@code git status} about one directory only, so other projects' changes do not count. */
public final class GitProjectChanges implements ProjectChanges {
  // The "." pathspec limits the status to the directory Git runs in.
  private static final List<String> STATUS = List.of("git", "status", "--porcelain", "--", ".");

  private final CommandExecutor executor;

  /** Creates the adapter; {@code executor} runs the read-only Git query. */
  @Inject
  public GitProjectChanges(final CommandExecutor executor) {
    this.executor = executor;
  }

  @Override
  public boolean changedBelow(final Path directory) {
    final CommandResult status = executor.execute(STATUS, directory);
    return !status.succeeded() || status.stdout() == null || !status.stdout().isBlank();
  }
}
