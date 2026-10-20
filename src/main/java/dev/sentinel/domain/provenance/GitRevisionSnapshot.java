package dev.sentinel.domain.provenance;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Immutable, deterministic content snapshot of the OpenSpec tree at one revision. */
public record GitRevisionSnapshot(String revision, Map<String, String> files) {
  /** Copies the files; missing values mean an empty snapshot. */
  public GitRevisionSnapshot {
    revision = revision == null ? "" : revision;
    files = Map.copyOf(new LinkedHashMap<>(files == null ? Map.of() : files));
  }

  /** Returns the content of {@code path} at this revision, if it exists. */
  public Optional<String> content(final String path) {
    return Optional.ofNullable(files.get(path));
  }

  /** Returns whether {@code path} exists at this revision. */
  public boolean contains(final String path) {
    return files.containsKey(path);
  }
}
