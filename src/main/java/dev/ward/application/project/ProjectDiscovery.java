package dev.ward.application.project;

import dev.ward.domain.project.DiscoveredProject;
import dev.ward.domain.project.ProjectInspection;
import java.nio.file.Path;
import java.util.List;

/** Project-discovery use case delegated to the project inspection port. */
public final class ProjectDiscovery {
  private final ProjectInspection inspection;

  /** Creates the discovery; {@code inspection} reads the file system. */
  public ProjectDiscovery(final ProjectInspection inspection) {
    this.inspection = inspection;
  }

  /** Returns every supported project under the repository containing {@code start}. */
  public List<DiscoveredProject> discover(final Path start) {
    return inspection.discover(start);
  }
}
