package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.IntegrationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Common ownership-safe filesystem operations for generated agent integrations. */
final class IntegrationArtifacts {
  private IntegrationArtifacts() {}

  /** One generated file: where it lives, how ownership is recognized, and what it contains. */
  record Target(Path path, String marker, String content) {}

  static boolean owned(final Path target, final String marker) throws IOException {
    return Files.exists(target) && Files.readString(target).contains(marker);
  }

  static IntegrationResult preflight(final Path target, final String marker) throws IOException {
    if (!Files.exists(target) || owned(target, marker)) {
      return null;
    }
    return new IntegrationResult(
        IntegrationResult.Status.CONFLICT,
        List.of(target.toString()),
        "Existing user-owned integration preserved; resolve the conflict explicitly.");
  }

  /** Returns the first conflict among ownership-marked targets, or {@code null}. */
  static IntegrationResult preflight(final List<Target> targets) throws IOException {
    for (final Target target : targets) {
      final IntegrationResult conflict = preflight(target.path(), target.marker());
      if (conflict != null) {
        return conflict;
      }
    }
    return null;
  }

  /**
   * Installs or refreshes every target after an all-target conflict preflight, so a conflict leaves
   * every target untouched.
   */
  static IntegrationResult installOrRefreshAll(
      final List<Target> targets, final String presentMessage, final String changedMessage)
      throws IOException {
    final IntegrationResult conflict = preflight(targets);
    if (conflict != null) {
      return conflict;
    }
    final List<String> changed = new ArrayList<>();
    for (final Target target : targets) {
      final IntegrationResult result =
          installOrRefresh(target.path(), target.marker(), target.content(), changedMessage);
      if (result.status() == IntegrationResult.Status.CONFLICT) {
        return result;
      }
      if (result.status() == IntegrationResult.Status.CHANGED) {
        changed.add(target.path().toString());
      }
    }
    return changed.isEmpty()
        ? new IntegrationResult(IntegrationResult.Status.ALREADY_PRESENT, changed, presentMessage)
        : new IntegrationResult(IntegrationResult.Status.CHANGED, changed, changedMessage);
  }

  /**
   * Removes every existing owned target after an all-target conflict preflight, then deletes
   * directories left empty, stopping before {@code cleanupBoundary}.
   */
  static IntegrationResult removeAll(
      final List<Target> targets,
      final Path cleanupBoundary,
      final String notFoundMessage,
      final String removedMessage)
      throws IOException {
    final IntegrationResult conflict = preflight(targets);
    if (conflict != null) {
      return conflict;
    }
    final List<String> removed = new ArrayList<>();
    for (final Target target : targets) {
      if (Files.exists(target.path())) {
        Files.delete(target.path());
        removed.add(target.path().toString());
      }
    }
    if (removed.isEmpty()) {
      return new IntegrationResult(IntegrationResult.Status.NOT_FOUND, removed, notFoundMessage);
    }
    for (final Target target : targets) {
      deleteEmptyAncestors(target.path().getParent(), cleanupBoundary);
    }
    return new IntegrationResult(IntegrationResult.Status.REMOVED, removed, removedMessage);
  }

  private static void deleteEmptyAncestors(final Path start, final Path boundary)
      throws IOException {
    Path directory = start;
    while (directory != null
        && !directory.equals(boundary)
        && directory.startsWith(boundary)
        && isEmptyDirectory(directory)) {
      Files.delete(directory);
      directory = directory.getParent();
    }
  }

  private static boolean isEmptyDirectory(final Path directory) throws IOException {
    if (!Files.isDirectory(directory)) {
      return false;
    }
    try (Stream<Path> entries = Files.list(directory)) {
      return entries.findAny().isEmpty();
    }
  }

  static IntegrationResult install(
      final Path target, final String marker, final String content, final String message)
      throws IOException {
    if (Files.exists(target)) {
      if (owned(target, marker)) {
        return new IntegrationResult(
            IntegrationResult.Status.ALREADY_PRESENT,
            List.of(),
            "Sentinel integration is already present.");
      }
      return new IntegrationResult(
          IntegrationResult.Status.CONFLICT,
          List.of(target.toString()),
          "Existing user-owned integration preserved; resolve the conflict explicitly.");
    }
    final Path parent = Objects.requireNonNull(target.getParent(), "target must have a parent");
    Files.createDirectories(parent);
    Files.writeString(target, content, StandardOpenOption.CREATE_NEW);
    return new IntegrationResult(
        IntegrationResult.Status.CHANGED, List.of(target.toString()), message);
  }

  static IntegrationResult installOrRefresh(
      final Path target, final String marker, final String content, final String message)
      throws IOException {
    if (Files.exists(target)) {
      if (!owned(target, marker)) {
        return new IntegrationResult(
            IntegrationResult.Status.CONFLICT,
            List.of(target.toString()),
            "Existing user-owned integration preserved; resolve the conflict explicitly.");
      }
      if (Objects.equals(Files.readString(target), content)) {
        return new IntegrationResult(
            IntegrationResult.Status.ALREADY_PRESENT,
            List.of(),
            "Sentinel integration is already present.");
      }
      Files.writeString(target, content, StandardOpenOption.TRUNCATE_EXISTING);
      return new IntegrationResult(
          IntegrationResult.Status.CHANGED, List.of(target.toString()), message);
    }
    return install(target, marker, content, message);
  }

  static IntegrationResult remove(final Path target, final String marker, final String message)
      throws IOException {
    if (!Files.exists(target)) {
      return new IntegrationResult(IntegrationResult.Status.NOT_FOUND, List.of(), message);
    }
    if (!owned(target, marker)) {
      return new IntegrationResult(
          IntegrationResult.Status.CONFLICT,
          List.of(target.toString()),
          "Refusing to remove an integration not owned by Sentinel.");
    }
    Files.delete(target);
    return new IntegrationResult(
        IntegrationResult.Status.REMOVED, List.of(target.toString()), message);
  }
}
