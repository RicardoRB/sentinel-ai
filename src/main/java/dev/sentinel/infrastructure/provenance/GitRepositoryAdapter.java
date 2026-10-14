package dev.sentinel.infrastructure.provenance;

import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.process.CommandResult;
import dev.sentinel.domain.provenance.GitChange;
import dev.sentinel.domain.provenance.GitRepository;
import dev.sentinel.domain.provenance.GitRevisionSnapshot;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

/** Git adapter using fixed argument vectors and deterministic output formats. */
public final class GitRepositoryAdapter implements GitRepository {
  private final CommandExecutor executor;

  @Inject
  public GitRepositoryAdapter(final CommandExecutor executor) {
    this.executor = executor;
  }

  @Override
  public String head(final Path repository) {
    return run(repository, List.of("git", "rev-parse", "--verify", "HEAD")).trim();
  }

  @Override
  public String resolveMergeBase(final Path repository, final String base, final String head) {
    return run(repository, List.of("git", "merge-base", "--", base, head)).trim();
  }

  @Override
  public GitRevisionSnapshot snapshot(final Path repository, final String revision) {
    final String names =
        run(
            repository,
            List.of("git", "ls-tree", "-r", "-z", "--name-only", revision, "--", "openspec"));
    final Map<String, String> files = new LinkedHashMap<>();
    for (final String path : names.split("\\u0000")) {
      if (!path.isBlank()) {
        files.put(path, run(repository, List.of("git", "show", revision + ":" + path)));
      }
    }
    return new GitRevisionSnapshot(revision, files);
  }

  @Override
  public List<GitChange> changes(final Path repository, final String base, final String head) {
    final String output =
        run(
            repository,
            List.of("git", "diff", "--name-status", "-M", "-z", base, head, "--", "openspec"));
    final List<String> fields =
        Arrays.stream(output.split("\\u0000")).filter(field -> !field.isBlank()).toList();
    final List<GitChange> changes = new ArrayList<>();
    int index = 0;
    while (index < fields.size()) {
      final String status = fields.get(index);
      final char kind = status.charAt(0);
      if (kind == 'R' && index + 2 < fields.size()) {
        final String oldPath = fields.get(index + 1);
        final String newPath = fields.get(index + 2);
        changes.add(GitChange.renamed(oldPath, newPath));
        index += 3;
      } else if (index + 1 < fields.size()) {
        final String path = fields.get(index + 1);
        changes.add(
            switch (kind) {
              case 'A' -> GitChange.added(path);
              case 'D' -> GitChange.deleted(path);
              default -> GitChange.modified(path);
            });
        index += 2;
      } else {
        index++;
      }
    }
    return changes.stream().sorted(Comparator.comparing(GitChange::newPath)).toList();
  }

  private String run(final Path repository, final List<String> command) {
    final CommandResult result = executor.execute(command, repository);
    if (!result.succeeded()) {
      throw new IllegalStateException(
          "Git prerequisite unavailable: "
              + (result.stderr().isBlank() ? result.stdout() : result.stderr()).trim());
    }
    return result.stdout();
  }
}
