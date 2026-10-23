package dev.ward.application.agent;

import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.project.DiscoveredProject;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.ProjectChanges;
import dev.ward.domain.spec.ProjectPaths;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Decides which projects a hook checks. A nested project shares the agent session with the rest of
 * the repository, so its turn-end check runs only when the turn left a change below the project. A
 * hook started outside any project, as repository-level native hooks are, checks each configured
 * project its edits, or at turn end its changes, fall in.
 */
public final class HookScope {
  /** Always checks the hook's own project, as for projects at the repository root. */
  public static final HookScope ALWAYS = new HookScope(null, null, null);

  private static final Logger LOGGER = Logger.getLogger(HookScope.class.getName());
  private static final Path CONFIGURATION = Path.of(WardConfiguration.FILE_NAME);
  private static final Path HERE = Path.of("");

  private final ProjectDetector detector;
  private final ProjectChanges changes;
  private final ProjectPaths paths;

  /**
   * Creates the scope; {@code changes} reports whether a project has changes and {@code paths}
   * whether it has a {@code ward.toml}.
   */
  public HookScope(
      final ProjectDetector detector, final ProjectChanges changes, final ProjectPaths paths) {
    this.detector = detector;
    this.changes = changes;
    this.paths = paths;
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

  /**
   * Returns the directories whose turn-end check runs for a hook started in {@code start}: {@code
   * start} itself inside a project, otherwise every configured project of the repository with
   * changes.
   */
  public List<Path> turnEndProjects(final Path start) {
    if (insideProject(start)) {
      return applies(start) ? List.of(start) : List.of();
    }
    final List<Path> changed =
        detector.discover(start).stream()
            .map(DiscoveredProject::project)
            .filter(this::configured)
            .map(Project::root)
            .filter(changes::changedBelow)
            .toList();
    LOGGER.log(Level.INFO, () -> "event=turn-end-scope routed=true projects=" + changed.size());
    return changed;
  }

  /**
   * Groups {@code edited} by the directory whose partial check covers them: {@code start} itself
   * inside a project, otherwise the configured project containing each path. Paths in no configured
   * project are dropped.
   */
  public Map<Path, List<Path>> editedProjects(final Path start, final List<Path> edited) {
    if (insideProject(start)) {
      return Map.of(start, edited);
    }
    final Map<Path, List<Path>> projects =
        edited.stream()
            .flatMap(path -> projectOf(path).map(root -> Map.entry(root, path)).stream())
            .collect(
                Collectors.groupingBy(
                    Map.Entry::getKey,
                    LinkedHashMap::new,
                    Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    LOGGER.log(Level.INFO, () -> "event=edit-scope routed=true projects=" + projects.size());
    return projects;
  }

  private Optional<Path> projectOf(final Path path) {
    return existingParent(path)
        .flatMap(detector::detect)
        .filter(this::configured)
        .map(Project::root);
  }

  /** A deleted file, or one in a deleted directory, belongs to the nearest directory left. */
  private Optional<Path> existingParent(final Path path) {
    Path directory = path.getParent();
    while (directory != null && !paths.isDirectory(directory, HERE)) {
      directory = directory.getParent();
    }
    return Optional.ofNullable(directory);
  }

  private boolean insideProject(final Path start) {
    return detector == null || detector.detect(start).isPresent();
  }

  private boolean configured(final Project project) {
    return paths.isRegularFile(project.root(), CONFIGURATION);
  }
}
