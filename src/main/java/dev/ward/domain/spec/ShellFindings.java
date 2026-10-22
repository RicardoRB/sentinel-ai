package dev.ward.domain.spec;

import java.util.ArrayList;
import java.util.List;

/** Accumulates what a shell command would do, relative to its starting directory. */
final class ShellFindings {
  private static final int MAX_NESTING = 3;

  private final int depth;
  private final List<GuardTarget> targets = new ArrayList<>();
  private String directory = "";
  private boolean inspectable = true;
  private boolean archive;

  ShellFindings(final int depth) {
    this.depth = depth;
  }

  /** Records a target unless it depends on an expansion that cannot be resolved statically. */
  void target(final GuardOperation operation, final ShellWord word) {
    if (!word.dynamic() && !word.text().isEmpty()) {
      targets.add(new GuardTarget(operation, located(word.text()), word.glob()));
    }
  }

  void archiveCommand() {
    archive = true;
  }

  /** Inspects a command passed to {@code sh -c} or {@code eval} as part of this command. */
  void nested(final String command) {
    if (depth >= MAX_NESTING) {
      inspectable = false;
      return;
    }
    final ShellInspection inner = ShellCommandHeuristic.inspect(command, depth + 1);
    if (!inner.inspectable()) {
      inspectable = false;
    }
    if (inner.archiveCommand()) {
      archive = true;
    }
    inner
        .targets()
        .forEach(
            target ->
                targets.add(
                    new GuardTarget(target.operation(), located(target.path()), target.glob())));
  }

  /** Follows {@code cd}; later relative paths resolve against the new directory. */
  void changeDirectory(final ShellWord word) {
    if (word.dynamic() || "-".equals(word.text())) {
      directory = "";
    } else {
      directory = located(word.text());
    }
  }

  ShellInspection result() {
    return inspectable
        ? new ShellInspection(true, archive, targets)
        : ShellInspection.uninspectable();
  }

  private String located(final String path) {
    return directory.isEmpty() || path.startsWith("/") ? path : directory + "/" + path;
  }
}
