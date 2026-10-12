package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.OpenSpecGuidanceLayout;
import dev.sentinel.domain.agent.OpenSpecSetup;
import dev.sentinel.domain.agent.OpenSpecSetupInspection;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;

/**
 * Reads OpenSpec prerequisites from the filesystem. Only a bare {@code openspec} on {@code PATH}
 * counts, because official agent workflows invoke it by name; executables configured for Sentinel's
 * gates are deliberately ignored. No process is started.
 */
public final class FileSystemOpenSpecSetupInspection implements OpenSpecSetupInspection {
  private static final List<String> EXECUTABLE_NAMES =
      List.of("openspec", "openspec.cmd", "openspec.exe");

  private final Supplier<String> path;

  @Inject
  public FileSystemOpenSpecSetupInspection() {
    this(() -> System.getenv("PATH"));
  }

  public FileSystemOpenSpecSetupInspection(final Supplier<String> path) {
    this.path = path;
  }

  @Override
  public OpenSpecSetup inspect(final Path projectRoot, final OpenSpecGuidanceLayout layout) {
    return new OpenSpecSetup(
        executableOnPath(),
        Files.isDirectory(projectRoot.resolve("openspec")),
        layout.verifyWorkflowFiles().stream()
            .anyMatch(file -> Files.isRegularFile(projectRoot.resolve(file))));
  }

  private boolean executableOnPath() {
    final String value = path.get();
    if (value == null || value.isBlank()) {
      return false;
    }
    return Arrays.stream(value.split(File.pathSeparator))
        .filter(entry -> !entry.isBlank())
        .anyMatch(FileSystemOpenSpecSetupInspection::containsExecutable);
  }

  private static boolean containsExecutable(final String directory) {
    try {
      final Path base = Path.of(directory);
      return EXECUTABLE_NAMES.stream()
          .map(base::resolve)
          .anyMatch(candidate -> Files.isRegularFile(candidate) && Files.isExecutable(candidate));
    } catch (InvalidPathException ignored) {
      return false;
    }
  }
}
