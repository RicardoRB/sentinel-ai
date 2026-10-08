package dev.sentinel.application.project;

import dev.sentinel.domain.project.Project;
import dev.sentinel.domain.project.ProjectInspection;
import java.nio.file.Path;
import java.util.List;

/** Project-discovery use case delegated to the project inspection port. */
public final class ProjectDiscovery {
  private final ProjectInspection inspection;

  public ProjectDiscovery(final ProjectInspection inspection) {
    this.inspection = inspection;
  }

  public List<Project> discover(final Path start) {
    return inspection.discover(start);
  }
}
