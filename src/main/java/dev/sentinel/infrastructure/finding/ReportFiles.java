package dev.sentinel.infrastructure.finding;

import dev.sentinel.domain.gate.FindingExtractionException;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Project;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Locates tool reports written during the current gate run and normalises reported paths. */
final class ReportFiles {
  private static final String FILE_SCHEME = "file://";
  private static final Logger LOGGER = Logger.getLogger(ReportFiles.class.getName());

  private ReportFiles() {}

  /**
   * Returns report files for the project's build tool that were modified at or after {@code
   * gateStart}. {@code mavenFile} is a single file; {@code gradleDirectory} holds one XML report
   * per source set.
   */
  static List<Path> freshReports(
      final Project project,
      final String gateId,
      final Instant gateStart,
      final String mavenFile,
      final String gradleDirectory) {
    final List<Path> candidates =
        project.buildTool() == BuildTool.GRADLE
            ? xmlFilesIn(project.root().resolve(gradleDirectory))
            : List.of(project.root().resolve(mavenFile));
    return fresh(gateId, gateStart, candidates);
  }

  /** Returns {@code report} when it exists and was modified at or after {@code gateStart}. */
  static List<Path> fresh(final String gateId, final Instant gateStart, final List<Path> reports) {
    // File systems may store modification times at second precision.
    final Instant threshold = gateStart.truncatedTo(ChronoUnit.SECONDS);
    final List<Path> fresh = new ArrayList<>();
    for (final Path report : reports) {
      if (!Files.isRegularFile(report)) {
        LOGGER.log(
            Level.INFO,
            () ->
                "event=finding-report-missing gate=" + gateId + " report=" + report.getFileName());
        continue;
      }
      if (modified(report).isBefore(threshold)) {
        LOGGER.log(
            Level.INFO,
            () -> "event=finding-report-stale gate=" + gateId + " report=" + report.getFileName());
        continue;
      }
      fresh.add(report);
    }
    return fresh;
  }

  /**
   * Returns {@code reported} relative to the project root with {@code /} separators, or as given
   * when it lies outside the root.
   */
  static String relativize(final Project project, final String reported) {
    if (reported == null || reported.isBlank()) {
      return null;
    }
    final String value =
        reported.startsWith(FILE_SCHEME) ? reported.substring(FILE_SCHEME.length()) : reported;
    final Path path = Path.of(value).normalize();
    if (!path.isAbsolute()) {
      return slashes(path.toString());
    }
    for (final Path root : roots(project.root())) {
      if (path.startsWith(root)) {
        return slashes(root.relativize(path).toString());
      }
    }
    return slashes(path.toString());
  }

  private static List<Path> roots(final Path root) {
    final Path absolute = root.toAbsolutePath().normalize();
    try {
      final Path real = absolute.toRealPath();
      return real.equals(absolute) ? List.of(absolute) : List.of(absolute, real);
    } catch (IOException exception) {
      return List.of(absolute);
    }
  }

  private static String slashes(final String path) {
    return path.replace('\\', '/');
  }

  private static List<Path> xmlFilesIn(final Path directory) {
    if (!Files.isDirectory(directory)) {
      return List.of(directory.resolve("main.xml"));
    }
    final List<Path> files = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.xml")) {
      stream.forEach(files::add);
    } catch (IOException exception) {
      throw new FindingExtractionException("Cannot read report", exception);
    }
    files.sort(null);
    return files;
  }

  private static Instant modified(final Path report) {
    try {
      return Files.getLastModifiedTime(report).toInstant();
    } catch (IOException exception) {
      throw new FindingExtractionException("Cannot read report", exception);
    }
  }
}
