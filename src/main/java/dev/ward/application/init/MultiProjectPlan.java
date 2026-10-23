package dev.ward.application.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.agent.PlannedFileChange.Action;
import dev.ward.domain.init.InitPlan;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The plans of several projects configured in one run: each project's own files, and the
 * repository-level files they share, listed once with the action the whole run takes on them.
 *
 * @param projects the projects, in run order
 * @param plans each project's plan, computed against the files on disk
 * @param shared the files outside every project root, merged across plans
 */
public record MultiProjectPlan(
    List<Project> projects, List<InitPlan> plans, List<PlannedFileChange> shared) {

  /** Copies the lists. */
  public MultiProjectPlan {
    projects = List.copyOf(projects);
    plans = List.copyOf(plans);
    shared = List.copyOf(shared);
  }

  /** Splits and merges {@code plans}, one per project in {@code projects}. */
  public static MultiProjectPlan of(final List<Project> projects, final List<InitPlan> plans) {
    final Map<Path, List<PlannedFileChange>> byFile =
        plans.stream()
            .flatMap(plan -> plan.files().stream())
            .filter(file -> outside(projects, file.file()))
            .collect(
                Collectors.groupingBy(
                    PlannedFileChange::file, LinkedHashMap::new, Collectors.toList()));
    return new MultiProjectPlan(
        projects, plans, byFile.values().stream().map(MultiProjectPlan::merge).toList());
  }

  /** Returns the files of the project at {@code index} that lie inside its root. */
  public List<PlannedFileChange> projectFiles(final int index) {
    final Path root = projects.get(index).root();
    return plans.get(index).visibleFiles().stream()
        .filter(file -> file.file().startsWith(root))
        .toList();
  }

  /** Whether any file in any plan would stop initialization. */
  public boolean hasConflict() {
    return plans.stream().anyMatch(InitPlan::hasConflict);
  }

  private static boolean outside(final List<Project> projects, final Path file) {
    return projects.stream().noneMatch(project -> file.startsWith(project.root()));
  }

  /**
   * Each plan saw the file as it is on disk, so a file one project creates is modified by the next:
   * the run creates it when any plan creates it, modifies it when any plan writes it, and stops on
   * it when any plan conflicts.
   */
  private static PlannedFileChange merge(final List<PlannedFileChange> changes) {
    final Set<Action> actions = new LinkedHashSet<>();
    final Set<String> details = new LinkedHashSet<>();
    changes.forEach(
        change -> {
          actions.add(change.action());
          details.addAll(change.details());
        });
    final Action action;
    if (actions.contains(Action.CONFLICT)) {
      action = Action.CONFLICT;
    } else if (actions.contains(Action.CREATE)) {
      action = Action.CREATE;
    } else if (actions.stream().anyMatch(Action::writes)) {
      action = Action.MODIFY;
    } else {
      action = changes.getFirst().action();
    }
    return new PlannedFileChange(changes.getFirst().file(), action, List.copyOf(details));
  }
}
