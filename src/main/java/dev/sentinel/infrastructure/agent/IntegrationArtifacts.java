package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.agent.PlannedFileChange;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Common ownership-safe filesystem operations for generated agent integrations. */
final class IntegrationArtifacts {
  private IntegrationArtifacts() {}

  static boolean owned(final Path target, final String marker) throws IOException {
    return Files.exists(target) && Files.readString(target).contains(marker);
  }

  static void makeExecutable(final Path file) throws IOException {
    try {
      Files.setPosixFilePermissions(
          file,
          EnumSet.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE,
              PosixFilePermission.GROUP_READ,
              PosixFilePermission.GROUP_EXECUTE,
              PosixFilePermission.OTHERS_READ,
              PosixFilePermission.OTHERS_EXECUTE));
    } catch (UnsupportedOperationException ignored) {
      file.toFile().setExecutable(true, false);
    }
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

  /** Returns the first conflict of two ownership-marked targets, or {@code null}. */
  static IntegrationResult preflight(
      final Path first, final String firstMarker, final Path second, final String secondMarker)
      throws IOException {
    final IntegrationResult conflict = preflight(first, firstMarker);
    return conflict == null ? preflight(second, secondMarker) : conflict;
  }

  /**
   * Combines artifact results into one, listing the targets that changed or were removed. The first
   * conflict, if any, is returned as is.
   */
  static IntegrationResult combine(
      final List<Map.Entry<Path, IntegrationResult>> results,
      final String presentMessage,
      final String changedMessage) {
    final Optional<IntegrationResult> conflict =
        results.stream()
            .map(Map.Entry::getValue)
            .filter(result -> result.status() == IntegrationResult.Status.CONFLICT)
            .findFirst();
    if (conflict.isPresent()) {
      return conflict.get();
    }
    final List<String> changed =
        results.stream()
            .filter(
                entry ->
                    entry.getValue().status() == IntegrationResult.Status.CHANGED
                        || entry.getValue().status() == IntegrationResult.Status.REMOVED)
            .map(entry -> entry.getKey().toString())
            .toList();
    return changed.isEmpty()
        ? new IntegrationResult(IntegrationResult.Status.ALREADY_PRESENT, changed, presentMessage)
        : new IntegrationResult(IntegrationResult.Status.CHANGED, changed, changedMessage);
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
    atomicWrite(target, content);
    return new IntegrationResult(
        IntegrationResult.Status.CHANGED, List.of(target.toString()), message);
  }

  /**
   * Decides what writing {@code content} to an ownership-marked {@code target} would do. {@link
   * #installOrRefresh} acts on this decision, so a plan built from it matches the write.
   */
  static PlannedFileChange.Action decide(
      final Path target, final String marker, final String content) throws IOException {
    if (!Files.exists(target)) {
      return PlannedFileChange.Action.CREATE;
    }
    if (!owned(target, marker)) {
      return PlannedFileChange.Action.CONFLICT;
    }
    return Objects.equals(Files.readString(target), content)
        ? PlannedFileChange.Action.UNCHANGED
        : PlannedFileChange.Action.MODIFY;
  }

  static IntegrationResult installOrRefresh(
      final Path target, final String marker, final String content, final String message)
      throws IOException {
    return switch (decide(target, marker, content)) {
      case CREATE -> install(target, marker, content, message);
      case CONFLICT ->
          new IntegrationResult(
              IntegrationResult.Status.CONFLICT,
              List.of(target.toString()),
              "Existing user-owned integration preserved; resolve the conflict explicitly.");
      case UNCHANGED ->
          new IntegrationResult(
              IntegrationResult.Status.ALREADY_PRESENT,
              List.of(),
              "Sentinel integration is already present.");
      default -> {
        atomicWrite(target, content);
        yield new IntegrationResult(
            IntegrationResult.Status.CHANGED, List.of(target.toString()), message);
      }
    };
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

  private static void atomicWrite(final Path target, final String content) throws IOException {
    final Path temporary =
        Files.createTempFile(
            Objects.requireNonNull(target.getParent(), "target must have a parent"),
            ".sentinel-",
            ".tmp");
    try {
      Files.writeString(temporary, content, StandardOpenOption.TRUNCATE_EXISTING);
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (UnsupportedOperationException exception) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
