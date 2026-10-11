package dev.sentinel.infrastructure.doctor;

import dev.sentinel.domain.doctor.EnvironmentFacts;
import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.doctor.ExecutableObservation;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/** Reads executable availability and prerequisite markers from the current machine. */
public final class SystemEnvironmentInspection implements EnvironmentInspection {
  private static final Logger LOGGER =
      Logger.getLogger(SystemEnvironmentInspection.class.getName());
  private static final Set<String> PROBED_EXECUTABLES =
      Set.of("mvn", "java", "git", "claude", "opencode");

  @Inject
  public SystemEnvironmentInspection() {}

  @Override
  public EnvironmentFacts inspect(final Path projectRoot) {
    final Set<String> available = new HashSet<>();
    final String path = System.getenv("PATH");
    if (path != null) {
      Arrays.stream(path.split(File.pathSeparator))
          .map(Path::of)
          .forEach(
              directory ->
                  PROBED_EXECUTABLES.stream()
                      .filter(name -> Files.isExecutable(directory.resolve(name)))
                      .forEach(available::add));
    }
    final Map<String, String> values =
        Map.of(
            "mavenWrapperAvailable",
                Boolean.toString(Files.isExecutable(projectRoot.resolve("mvnw"))),
            "configurationPresent",
                Boolean.toString(Files.isRegularFile(projectRoot.resolve("sentinel.toml"))),
            "openCodeCommandPresent",
                Boolean.toString(
                    Files.isRegularFile(
                        projectRoot.resolve(".opencode/commands/sentinel-check.md"))),
            "nativeExecutablePresent",
                Boolean.toString(Files.isExecutable(Path.of("target/sentinel"))));
    LOGGER.log(
        Level.INFO,
        () -> "event=environment-inspection status=complete available=" + available.size());
    return new EnvironmentFacts(available, values);
  }

  @Override
  public ExecutableObservation inspectExecutable(final Path projectRoot, final String executable) {
    final Path candidate;
    if (executable.contains("/") || executable.contains(File.separator)) {
      candidate =
          Path.of(executable).isAbsolute()
              ? Path.of(executable)
              : projectRoot.resolve(executable).normalize();
    } else {
      candidate = findOnPath(executable);
    }
    if (candidate == null) {
      return new ExecutableObservation(executable, null, false, false, false);
    }
    return new ExecutableObservation(
        executable,
        candidate,
        Files.exists(candidate),
        Files.isRegularFile(candidate),
        Files.isExecutable(candidate));
  }

  @Override
  public boolean hasOpenSpecDirectory(final Path projectRoot) {
    return Files.isDirectory(projectRoot.resolve("openspec"));
  }

  private static Path findOnPath(final String executable) {
    final String path = System.getenv("PATH");
    if (path == null) {
      return null;
    }
    return Arrays.stream(path.split(File.pathSeparator))
        .map(Path::of)
        .map(directory -> directory.resolve(executable))
        .filter(Files::exists)
        .findFirst()
        .orElse(null);
  }
}
