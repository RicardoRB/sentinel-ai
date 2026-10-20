package dev.sentinel.domain.init;

import java.nio.file.Path;
import java.util.List;

/** Reversible Maven build configuration change. */
public record PomChange(
    Path file, String originalContent, List<String> tools, List<String> warnings) {
  /** Copies the lists; missing warnings mean none. */
  public PomChange {
    tools = List.copyOf(tools);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }

  /** Creates a change without warnings. */
  public PomChange(final Path file, final String originalContent, final List<String> tools) {
    this(file, originalContent, tools, List.of());
  }

  /** Returns whether any plugin was added or updated. */
  public boolean changed() {
    return !tools.isEmpty();
  }
}
