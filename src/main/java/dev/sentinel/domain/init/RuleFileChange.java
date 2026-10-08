package dev.sentinel.domain.init;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reversible rule-file update, including user-owned files that were preserved. */
public record RuleFileChange(
    Path configDirectory,
    boolean createdDirectory,
    Map<Path, String> originals,
    List<Path> preserved) {
  public RuleFileChange {
    originals = Collections.unmodifiableMap(new LinkedHashMap<>(originals));
    preserved = List.copyOf(preserved);
  }
}
