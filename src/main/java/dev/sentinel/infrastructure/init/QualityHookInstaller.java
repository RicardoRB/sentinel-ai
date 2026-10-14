package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.agent.IntegrationResult;
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
