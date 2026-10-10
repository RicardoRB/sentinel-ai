package dev.sentinel.application.project;

import dev.sentinel.domain.project.Project;
import dev.sentinel.domain.project.ProjectInspection;
import java.nio.file.Path;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/** Project-detection use case delegated to the project inspection port. */
public final class ProjectDetector {
  public static final String POM = "pom.xml";
  private static final Logger LOGGER = Logger.getLogger(ProjectDetector.class.getName());
  private final ProjectInspection inspection;

  @Inject
  public ProjectDetector(final ProjectInspection inspection) {
    this.inspection = inspection;
  }

  public Optional<Project> detect(final Path start) {
    final Optional<Project> result = inspection.detect(start);
    LOGGER.log(
        Level.INFO,
        () -> "event=project-detection status=" + (result.isPresent() ? "found" : "not-found"));
    return result;
  }
}
