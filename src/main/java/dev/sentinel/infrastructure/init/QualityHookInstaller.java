package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.agent.PlannedFileChange;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.QualityHookChange;
import dev.sentinel.domain.init.QualityHookChange.HookSnapshot;
import dev.sentinel.domain.init.QualityHookGeneration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/** Installs and removes opt-in quality checks in local Git hooks. */
public final class QualityHookInstaller implements QualityHookGeneration {
  public static final String START_MARKER = "# sentinel-owned: quality-hooks v1 begin";
  public static final String END_MARKER = "# sentinel-owned: quality-hooks v1 end";

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public QualityHookInstaller() {}

  @Override
  public QualityHookChange apply(final Path projectRoot) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final List<Path> paths =
        List.of(root.resolve(".git/hooks/pre-commit"), root.resolve(".git/hooks/pre-push"));
    try {
      final List<String> originals = new ArrayList<>();
      for (final Path path : paths) {
        originals.add(QualityHookFileOperations.readOrEmpty(path));
        QualityHookFileOperations.rejectMalformed(path, originals.getLast());
      }
      final List<HookSnapshot> snapshots =
          paths.stream().map(QualityHookFileOperations::snapshot).toList();
      final List<String> changed = new ArrayList<>();
      QualityHookFileOperations.writeHook(
          paths.get(0), QualityHookFileOperations.update(originals.get(0), "pre-commit"), changed);
      QualityHookFileOperations.writeHook(
          paths.get(1), QualityHookFileOperations.update(originals.get(1), "pre-push"), changed);
      final IntegrationResult result =
          changed.isEmpty()
              ? new IntegrationResult(
                  IntegrationResult.Status.ALREADY_PRESENT,
                  List.of(),
                  "Sentinel quality hooks are already present.")
              : new IntegrationResult(
                  IntegrationResult.Status.CHANGED, changed, "Installed Sentinel quality hooks.");
      return new QualityHookChange(snapshots, result);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not update quality hooks", e);
    }
  }

  @Override
  public List<PlannedFileChange> plan(final Path projectRoot) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    try {
      return List.of(
          planHook(root.resolve(".git/hooks/pre-commit"), "pre-commit"),
          planHook(root.resolve(".git/hooks/pre-push"), "pre-push"));
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read quality hooks", e);
    }
  }

  /** Uses the same read, marker check, and section update as {@link #apply}. */
  private static PlannedFileChange planHook(final Path path, final String profile)
      throws IOException {
    final String original = QualityHookFileOperations.readOrEmpty(path);
    try {
      QualityHookFileOperations.rejectMalformed(path, original);
    } catch (SentinelException malformed) {
      return new PlannedFileChange(
          path, PlannedFileChange.Action.CONFLICT, List.of(malformed.getMessage()));
    }
    final String updated = QualityHookFileOperations.update(original, profile);
    if (!Files.exists(path)) {
      return new PlannedFileChange(
          path, PlannedFileChange.Action.CREATE, List.of("Sentinel " + profile + " section"));
    }
    return updated.equals(original)
        ? new PlannedFileChange(path, PlannedFileChange.Action.UNCHANGED)
        : new PlannedFileChange(
            path, PlannedFileChange.Action.MODIFY, List.of("Sentinel " + profile + " section"));
  }

  /** Removes only Sentinel quality sections and wholly Sentinel-owned hook files. */
  @Override
  public IntegrationResult remove(final Path projectRoot) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final List<Path> paths =
        List.of(root.resolve(".git/hooks/pre-commit"), root.resolve(".git/hooks/pre-push"));
    try {
      final List<String> changed = new ArrayList<>();
      for (final Path path : paths) {
        removeHook(path, changed);
      }
      return changed.isEmpty()
          ? new IntegrationResult(
              IntegrationResult.Status.NOT_FOUND, List.of(), "Sentinel quality hooks not found.")
          : new IntegrationResult(
              IntegrationResult.Status.REMOVED, changed, "Removed Sentinel quality hooks.");
    } catch (IOException e) {
      throw new UncheckedIOException("Could not remove quality hooks", e);
    }
  }

  private static void removeHook(final Path path, final List<String> changed) throws IOException {
    if (!Files.exists(path)) {
      return;
    }
    final String content = Files.readString(path);
    QualityHookFileOperations.rejectMalformed(path, content);
    if (!content.contains(START_MARKER)) {
      return;
    }
    final String remaining = QualityHookFileOperations.removeSection(content);
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
