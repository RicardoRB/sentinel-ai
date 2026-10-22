package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.GuardMode;
import dev.ward.domain.agent.IntegrationResult;
import dev.ward.domain.agent.PlannedFileChange;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Ward-owned file that holds an agent's opt-in approved-artifact guard. Its presence is the
 * guard's installed state, so refreshing an integration without a guard option keeps it.
 */
record GuardFile(Path file, String marker, String content) {

  /** Whether a Ward-owned guard is installed; a user-owned file at the path does not count. */
  boolean installed() throws IOException {
    return IntegrationArtifacts.owned(file, marker);
  }

  /** Whether the guard should be installed after this run. */
  boolean enabled(final GuardMode mode) throws IOException {
    return mode == GuardMode.ENABLE || mode == GuardMode.KEEP && installed();
  }

  /** Returns a conflict when the guard is requested but a user-owned file is in the way. */
  IntegrationResult preflight(final GuardMode mode) throws IOException {
    return mode == GuardMode.ENABLE ? IntegrationArtifacts.preflight(file, marker) : null;
  }

  /**
   * Plans what {@link #install} or {@link #remove} would do for {@code mode}: refresh an enabled
   * guard, delete an installed guard that is being disabled, or nothing.
   */
  Optional<PlannedFileChange> plan(final GuardMode mode) throws IOException {
    if (enabled(mode)) {
      return Optional.of(
          new PlannedFileChange(file, IntegrationArtifacts.decide(file, marker, content)));
    }
    if (mode == GuardMode.DISABLE && installed()) {
      return Optional.of(new PlannedFileChange(file, PlannedFileChange.Action.REMOVE));
    }
    return Optional.empty();
  }

  Map.Entry<Path, IntegrationResult> install(final String message) throws IOException {
    return Map.entry(file, IntegrationArtifacts.installOrRefresh(file, marker, content, message));
  }

  /** Deletes an installed guard when requested; a user-owned file is left untouched. */
  Optional<Map.Entry<Path, IntegrationResult>> remove(final boolean requested) throws IOException {
    if (!requested || !installed()) {
      return Optional.empty();
    }
    Files.delete(file);
    return Optional.of(
        Map.entry(
            file,
            new IntegrationResult(
                IntegrationResult.Status.REMOVED,
                List.of(file.toString()),
                "Removed the Ward-owned approved-artifact guard.")));
  }
}
