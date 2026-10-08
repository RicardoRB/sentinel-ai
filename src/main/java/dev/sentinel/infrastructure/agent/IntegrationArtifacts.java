package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.IntegrationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;

/** Common ownership-safe filesystem operations for generated agent integrations. */
final class IntegrationArtifacts {
  private IntegrationArtifacts() {}

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
