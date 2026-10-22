package dev.ward.domain.agent;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** One file that initialization would create, modify, remove, keep, or stop on. */
public record PlannedFileChange(Path file, Action action, List<String> details) {
  /**
   * Validates the change and copies its details.
   *
   * @throws NullPointerException if the file or action is missing
   */
  public PlannedFileChange {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(action, "action");
    details = details == null ? List.of() : List.copyOf(details);
  }

  /** Creates a change without details. */
  public PlannedFileChange(final Path file, final Action action) {
    this(file, action, List.of());
  }

  /** What initialization would do with a file. */
  public enum Action {
    CREATE,
    MODIFY,
    REMOVE,
    UNCHANGED,
    PRESERVE_USER_OWNED,
    CONFLICT;

    /** Whether applying the plan changes the file on disk. */
    public boolean writes() {
      return this == CREATE || this == MODIFY || this == REMOVE;
    }
  }
}
