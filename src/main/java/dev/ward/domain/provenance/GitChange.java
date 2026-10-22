package dev.ward.domain.provenance;

/** One deterministic name-status entry between two Git snapshots. */
public record GitChange(Status status, String oldPath, String newPath) {
  /** How a path changed between two revisions. */
  public enum Status {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED
  }

  /** Replaces missing values: the status defaults to modified and the paths to empty. */
  public GitChange {
    status = status == null ? Status.MODIFIED : status;
    oldPath = oldPath == null ? "" : oldPath;
    newPath = newPath == null ? "" : newPath;
  }

  /** Creates the change for an added {@code path}. */
  public static GitChange added(final String path) {
    return new GitChange(Status.ADDED, "", path);
  }

  /** Creates the change for a modified {@code path}. */
  public static GitChange modified(final String path) {
    return new GitChange(Status.MODIFIED, path, path);
  }

  /** Creates the change for a deleted {@code path}. */
  public static GitChange deleted(final String path) {
    return new GitChange(Status.DELETED, path, "");
  }

  /** Creates the change for a path renamed {@code from} one path {@code to} another. */
  public static GitChange renamed(final String from, final String to) {
    return new GitChange(Status.RENAMED, from, to);
  }
}
