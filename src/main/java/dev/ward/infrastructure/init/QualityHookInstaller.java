package dev.ward.infrastructure.init;

import dev.ward.domain.agent.IntegrationResult;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.QualityHookChange;
import dev.ward.domain.init.QualityHookChange.HookSnapshot;
import dev.ward.domain.init.QualityHookGeneration;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.process.ProcessCommandExecutor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/**
 * Installs and removes opt-in quality checks in the Git hooks Git actually runs, as one section per
 * project so several Ward projects can share a repository's hooks.
 */
public final class QualityHookInstaller implements QualityHookGeneration {
  public static final String START_MARKER = HookSection.START_MARKER;
  public static final String END_MARKER = HookSection.END_MARKER;
  private static final String PRE_COMMIT = "pre-commit";
  private static final String PRE_PUSH = "pre-push";

  private final GitHooksDirectory hooksDirectory;

  /** Creates the adapter asking Git for the hooks directory through a real process. */
  public QualityHookInstaller() {
    this(new GitHooksDirectory(new ProcessCommandExecutor()));
  }

  /** Creates the adapter; {@code hooksDirectory} finds where Git runs hooks. */
  @Inject
  public QualityHookInstaller(final GitHooksDirectory hooksDirectory) {
    this.hooksDirectory = hooksDirectory;
  }

  @Override
  public QualityHookChange apply(final Project project) {
    final HookSection section = new HookSection(project.projectPath());
    final List<Path> paths = paths(project);
    try {
      final List<String> originals = new ArrayList<>();
      for (final Path path : paths) {
        originals.add(QualityHookFileOperations.readOrEmpty(path));
        QualityHookFileOperations.rejectMalformed(path, originals.getLast(), section);
      }
      final List<HookSnapshot> snapshots =
          paths.stream().map(QualityHookFileOperations::snapshot).toList();
      final List<String> changed = new ArrayList<>();
      QualityHookFileOperations.writeHook(
          paths.get(0),
          QualityHookFileOperations.update(originals.get(0), section, PRE_COMMIT),
          changed);
      QualityHookFileOperations.writeHook(
          paths.get(1),
          QualityHookFileOperations.update(originals.get(1), section, PRE_PUSH),
          changed);
      final IntegrationResult result =
          changed.isEmpty()
              ? new IntegrationResult(
                  IntegrationResult.Status.ALREADY_PRESENT,
                  List.of(),
                  "Ward quality hooks are already present.")
              : new IntegrationResult(
                  IntegrationResult.Status.CHANGED, changed, "Installed Ward quality hooks.");
      return new QualityHookChange(snapshots, result);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not update quality hooks", e);
    }
  }

  @Override
  public List<PlannedFileChange> plan(final Project project) {
    final List<Path> paths = paths(project);
    final HookSection section;
    try {
      section = new HookSection(project.projectPath());
    } catch (WardException unsafe) {
      return paths.stream()
          .map(
              path ->
                  new PlannedFileChange(
                      path, PlannedFileChange.Action.CONFLICT, List.of(unsafe.getMessage())))
          .toList();
    }
    try {
      return List.of(
          planHook(paths.get(0), section, PRE_COMMIT), planHook(paths.get(1), section, PRE_PUSH));
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read quality hooks", e);
    }
  }

  /** Uses the same read, marker check, and section update as {@link #apply}. */
  private static PlannedFileChange planHook(
      final Path path, final HookSection section, final String profile) throws IOException {
    final String original = QualityHookFileOperations.readOrEmpty(path);
    try {
      QualityHookFileOperations.rejectMalformed(path, original, section);
    } catch (WardException malformed) {
      return new PlannedFileChange(
          path, PlannedFileChange.Action.CONFLICT, List.of(malformed.getMessage()));
    }
    final String updated = QualityHookFileOperations.update(original, section, profile);
    if (!Files.exists(path)) {
      return new PlannedFileChange(
          path, PlannedFileChange.Action.CREATE, List.of("Ward " + profile + " section"));
    }
    return updated.equals(original)
        ? new PlannedFileChange(path, PlannedFileChange.Action.UNCHANGED)
        : new PlannedFileChange(
            path, PlannedFileChange.Action.MODIFY, List.of("Ward " + profile + " section"));
  }

  /** Removes only this project's Ward section, and hook files that held nothing else. */
  @Override
  public IntegrationResult remove(final Project project) {
    final HookSection section = new HookSection(project.projectPath());
    try {
      final List<String> changed = new ArrayList<>();
      for (final Path path : paths(project)) {
        removeHook(path, section, changed);
      }
      return changed.isEmpty()
          ? new IntegrationResult(
              IntegrationResult.Status.NOT_FOUND, List.of(), "Ward quality hooks not found.")
          : new IntegrationResult(
              IntegrationResult.Status.REMOVED, changed, "Removed Ward quality hooks.");
    } catch (IOException e) {
      throw new UncheckedIOException("Could not remove quality hooks", e);
    }
  }

  private List<Path> paths(final Project project) {
    final Path hooks = hooksDirectory.resolve(project);
    return List.of(hooks.resolve(PRE_COMMIT), hooks.resolve(PRE_PUSH));
  }

  private static void removeHook(
      final Path path, final HookSection section, final List<String> changed) throws IOException {
    if (!Files.exists(path)) {
      return;
    }
    final String content = Files.readString(path);
    QualityHookFileOperations.rejectMalformed(path, content, section);
    if (!QualityHookFileOperations.contains(content, section)) {
      return;
    }
    final String remaining = QualityHookFileOperations.removeSection(content, section);
    if (remaining.isBlank()
        || "#!/bin/sh".equals(remaining.trim())
        || "#!/bin/bash".equals(remaining.trim())) {
      Files.delete(path);
    } else {
      Files.writeString(path, remaining);
    }
    changed.add(path.toString());
  }

  @Override
  public void rollback(final QualityHookChange change) {
    if (change == null) {
      return;
    }
    try {
      for (final HookSnapshot snapshot : change.snapshots()) {
        if (!snapshot.existed()) {
          Files.deleteIfExists(snapshot.path());
          continue;
        }
        final Path parent = snapshot.path().toAbsolutePath().getParent();
        if (parent != null) {
          Files.createDirectories(parent);
        }
        Files.writeString(snapshot.path(), snapshot.content());
        snapshot.path().toFile().setExecutable(snapshot.executable(), false);
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Could not roll back quality hooks", e);
    }
  }
}
