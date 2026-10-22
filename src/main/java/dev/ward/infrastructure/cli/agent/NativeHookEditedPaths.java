package dev.ward.infrastructure.cli.agent;

import dev.ward.domain.agent.NativeHookInput;
import dev.ward.domain.spec.PatchTargets;
import java.nio.file.Path;
import java.util.List;

/** The files a native edit event touched, for the partial check that follows the edit. */
final class NativeHookEditedPaths {
  private NativeHookEditedPaths() {}

  /**
   * Returns the event's path, or every path its patch adds, updates, deletes, or moves, resolved
   * against {@code directory}, each once. The list is empty when the event names no recognizable
   * path.
   */
  static List<Path> of(final NativeHookInput event, final Path directory) {
    final Path base = directory.toAbsolutePath();
    if (event.path() != null && !event.path().isBlank()) {
      return List.of(base.resolve(event.path()).normalize());
    }
    return PatchTargets.parse(event.patch())
        .map(
            targets ->
                targets.stream()
                    .map(target -> base.resolve(target.path()).normalize())
                    .distinct()
                    .toList())
        .orElse(List.of());
  }
}
