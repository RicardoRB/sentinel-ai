package dev.sentinel.domain.doctor;

import java.util.Map;
import java.util.Set;

/** Immutable environment observations used by setup and diagnostics policy. */
public record EnvironmentFacts(Set<String> availableExecutables, Map<String, String> values) {
  /** Copies the observations. */
  public EnvironmentFacts {
    availableExecutables = Set.copyOf(availableExecutables);
    values = Map.copyOf(values);
  }

  /** Returns whether the executable {@code name} was found on the path. */
  public boolean hasExecutable(final String name) {
    return availableExecutables.contains(name);
  }

  /** Returns only whether a named environment variable is non-blank; never exposes its value. */
  public boolean isSet(final String name) {
    final String marker = values.get(name);
    return marker != null && !marker.isBlank() && !"false".equalsIgnoreCase(marker);
  }
}
