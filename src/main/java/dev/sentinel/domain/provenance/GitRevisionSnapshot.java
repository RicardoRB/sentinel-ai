package dev.sentinel.domain.provenance;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Immutable, deterministic content snapshot of the OpenSpec tree at one revision. */
public record GitRevisionSnapshot(String revision, Map<String, String> files) {
  public GitRevisionSnapshot {
    revision = revision == null ? "" : revision;
    files = Map.copyOf(new LinkedHashMap<>(files == null ? Map.of() : files));
  }

  public Optional<String> content(final String path) {
    return Optional.ofNullable(files.get(path));
  }

  public boolean contains(final String path) {
    return files.containsKey(path);
  }
}
