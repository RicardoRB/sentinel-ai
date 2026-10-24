package dev.ward.domain.init;

import dev.ward.domain.project.Toolchain;
import java.nio.file.Path;
import java.util.List;

/**
 * Reversible build-file configuration change.
 *
 * @param toolchain the toolchain whose adapter made the change and can roll it back
 * @param file the edited build file
 * @param originalContent the file content before the change
 * @param tools the plugins added or updated
 * @param warnings compatibility warnings raised while editing
 */
public record BuildFileChange(
    Toolchain toolchain,
    Path file,
    String originalContent,
    List<String> tools,
    List<String> warnings) {
  /** Copies the lists; missing warnings mean none. */
  public BuildFileChange {
    tools = List.copyOf(tools);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }

  /** Creates a change without warnings. */
  public BuildFileChange(
      final Toolchain toolchain,
      final Path file,
      final String originalContent,
      final List<String> tools) {
    this(toolchain, file, originalContent, tools, List.of());
  }

  /** Returns whether any plugin was added or updated. */
  public boolean changed() {
    return !tools.isEmpty();
  }
}
