package dev.ward.domain.provenance;

/** Classification of an archive directory relative to the merge-base snapshot. */
public record ArchiveClassification(String path, Kind kind) {
  /** Whether the archive is new, an existing archive that changed, or unchanged. */
  public enum Kind {
    NEW,
    EXISTING_MUTATED,
    UNCHANGED
  }
}
