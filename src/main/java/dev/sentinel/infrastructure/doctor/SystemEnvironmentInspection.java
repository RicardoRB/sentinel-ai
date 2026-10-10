package dev.sentinel.infrastructure.doctor;

import dev.sentinel.domain.doctor.EnvironmentFacts;
import dev.sentinel.domain.doctor.EnvironmentInspection;
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
}
