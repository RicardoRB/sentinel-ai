package dev.sentinel.application.project;

import dev.sentinel.domain.project.Project;
import dev.sentinel.domain.project.ProjectInspection;
import java.nio.file.Path;
import java.util.Optional;
import javax.inject.Inject;

/** Project-detection use case delegated to the project inspection port. */
public final class ProjectDetector {
  public static final String POM = "pom.xml";
  private final ProjectInspection inspection;

  @Inject
  public ProjectDetector(ProjectInspection inspection) {
    this.inspection = inspection;
  }

  public Optional<Project> detect(Path start) {
    return inspection.detect(start);
  }
}
