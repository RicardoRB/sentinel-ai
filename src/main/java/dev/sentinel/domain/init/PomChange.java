package dev.sentinel.domain.init;

import java.nio.file.Path;
import java.util.List;

/** Reversible Maven build configuration change. */
public record PomChange(Path file, String originalContent, List<String> tools) {
  public PomChange {
    tools = List.copyOf(tools);
  }

  public boolean changed() {
    return !tools.isEmpty();
  }
}
