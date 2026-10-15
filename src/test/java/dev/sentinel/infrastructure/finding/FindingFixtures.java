package dev.sentinel.infrastructure.finding;

import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

final class FindingFixtures {
  private FindingFixtures() {}

  static Project maven(final Path root) {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE, true);
  }

  static Project gradle(final Path root) {
    return new Project(root, Language.JAVA, BuildTool.GRADLE, Framework.NONE, true);
  }

  static GateResult failed(final String gate, final String stdout) {
    return new GateResult(gate, GateStatus.FAILED, List.of("x"), 1, Duration.ZERO, stdout, "");
  }

  static GateResult failedWithStderr(final String gate, final String stderr) {
    return new GateResult(gate, GateStatus.FAILED, List.of("x"), 1, Duration.ZERO, "", stderr);
  }

  static Path write(final Path file, final String content) {
    try {
      Files.createDirectories(file.getParent());
      return Files.writeString(file, content);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  static void age(final Path file, final Duration age) {
    try {
      Files.setLastModifiedTime(file, FileTime.from(Instant.now().minus(age)));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  /** A gate start slightly in the past, so freshly written fixtures count as current. */
  static Instant justBefore() {
    return Instant.now().minusSeconds(5);
  }
}
