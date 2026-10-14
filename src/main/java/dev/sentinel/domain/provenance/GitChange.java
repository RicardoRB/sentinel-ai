package dev.sentinel.domain.provenance;

/** One deterministic name-status entry between two Git snapshots. */
public record GitChange(Status status, String oldPath, String newPath) {
  public enum Status {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED
  }

  public GitChange {
    status = status == null ? Status.MODIFIED : status;
    oldPath = oldPath == null ? "" : oldPath;
    newPath = newPath == null ? "" : newPath;
  }

  public static GitChange added(final String path) {
    return new GitChange(Status.ADDED, "", path);
  }

  public static GitChange modified(final String path) {
    return new GitChange(Status.MODIFIED, path, path);
  }

  public static GitChange deleted(final String path) {
    return new GitChange(Status.DELETED, path, "");
  }

  public static GitChange renamed(final String from, final String to) {
    return new GitChange(Status.RENAMED, from, to);
  }
}
