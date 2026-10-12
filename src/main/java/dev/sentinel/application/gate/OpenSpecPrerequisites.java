package dev.sentinel.application.gate;

import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.doctor.ExecutableObservation;
import java.nio.file.Path;
import java.util.Optional;

/** Shared OpenSpec executable and project-directory prerequisite checks. */
public final class OpenSpecPrerequisites {

  /** A prerequisite that is not met; {@code unavailable} means the executable cannot be used. */
  public record Problem(boolean unavailable, String message) {}

  private OpenSpecPrerequisites() {}

  /** Returns the first unmet prerequisite, inspecting without executing anything. */
  public static Optional<Problem> check(
      final EnvironmentInspection environment, final Path projectRoot, final String executable) {
    final ExecutableObservation observation =
        environment.inspectExecutable(projectRoot, executable);
    final boolean directory = environment.hasOpenSpecDirectory(projectRoot);
    if (!observation.usable()) {
      return Optional.of(
          new Problem(
              true,
              "OpenSpec executable '"
                  + executable
                  + "' is unavailable. Install OpenSpec or configure an executable path."
                  + (directory
                      ? ""
                      : " The project-root openspec/ directory is also missing or not a"
                          + " directory.")));
    }
    if (!directory) {
      return Optional.of(
          new Problem(
              false,
              "Project-root openspec/ directory is missing or is not a directory. Initialize the"
                  + " project manually."));
    }
    return Optional.empty();
  }
}
