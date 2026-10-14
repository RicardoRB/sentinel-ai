package dev.sentinel.domain.provenance;

/** Stable, safe-to-render enforcement finding. */
public record EnforcementFinding(Category category, String path, String message) {
  public enum Category {
    PREREQUISITE,
    CONFIGURED_CHECK,
    ARCHIVE_TASK,
    ARCHIVE_IMMUTABILITY,
    PROVENANCE,
    BOOTSTRAP
  }

  public EnforcementFinding {
    category = category == null ? Category.PROVENANCE : category;
    path = path == null ? "" : path;
    message = message == null ? "" : message;
  }
}
