package dev.ward.application.project;

import dev.ward.domain.project.DiscoveredProject;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.ProjectInspection;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/** Project-detection use case delegated to the project inspection port. */
public final class ProjectDetector {
  public static final String POM = "pom.xml";
  private static final Logger LOGGER = Logger.getLogger(ProjectDetector.class.getName());
  private final ProjectInspection inspection;

  /** Creates the detector; {@code inspection} reads the file system. */
  @Inject
  public ProjectDetector(final ProjectInspection inspection) {
    this.inspection = inspection;
  }

  /** Returns the project containing {@code start}, if it is supported. */
  public Optional<Project> detect(final Path start) {
    final Optional<Project> result = inspection.detect(start);
    LOGGER.log(
        Level.INFO,
        () -> "event=project-detection status=" + (result.isPresent() ? "found" : "not-found"));
    return result;
  }

  /**
   * Returns every supported project under the repository containing {@code start}, with build
   * modules grouped under their aggregator.
   */
  public List<DiscoveredProject> discover(final Path start) {
    final List<DiscoveredProject> result = inspection.discover(start);
    LOGGER.log(Level.INFO, () -> "event=project-discovery count=" + result.size());
    return result;
  }
}
