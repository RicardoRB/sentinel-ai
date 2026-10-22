package dev.ward.application.agent;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.ProjectChanges;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Decides whether a turn-end check runs. A nested project shares the agent session with the rest of
 * the repository, so its check runs only when the turn left a change below the project.
 */
public final class TurnEndScope {
  /** Runs every turn-end check, as for projects at the repository root. */
  public static final TurnEndScope ALWAYS = new TurnEndScope(null, null);

  private static final Logger LOGGER = Logger.getLogger(TurnEndScope.class.getName());

  private final ProjectDetector detector;
  private final ProjectChanges changes;

  /** Creates the scope; {@code changes} reports whether a nested project has changes. */
  public TurnEndScope(final ProjectDetector detector, final ProjectChanges changes) {
    this.detector = detector;
    this.changes = changes;
  }

  /** Returns whether the turn-end check of the project containing {@code start} should run. */
  public boolean applies(final Path start) {
    if (detector == null) {
      return true;
    }
    final Optional<Project> project = detector.detect(start);
    if (project.isEmpty() || !project.get().nested()) {
      return true;
    }
    final boolean changed = changes.changedBelow(project.get().root());
    LOGGER.log(Level.INFO, () -> "event=turn-end-scope nested=true changed=" + changed);
    return changed;
  }
}
