package dev.sentinel.domain.provenance;

/** Classification of an archive directory relative to the merge-base snapshot. */
public record ArchiveClassification(String path, Kind kind) {
  public enum Kind {
    NEW,
    EXISTING_MUTATED,
    UNCHANGED
  }
}
