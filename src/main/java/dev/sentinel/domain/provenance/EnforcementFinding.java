package dev.sentinel.domain.provenance;

/** Stable, safe-to-render enforcement finding. */
public record EnforcementFinding(Category category, String path, String message) {
  /** What kind of rule an enforcement finding reports. */
  public enum Category {
    PREREQUISITE,
    CONFIGURED_CHECK,
    ARCHIVE_TASK,
    ARCHIVE_IMMUTABILITY,
    PROVENANCE,
    BOOTSTRAP
  }

  /** Replaces missing values: the category defaults to provenance and the texts to empty. */
  public EnforcementFinding {
    category = category == null ? Category.PROVENANCE : category;
    path = path == null ? "" : path;
    message = message == null ? "" : message;
  }
}
