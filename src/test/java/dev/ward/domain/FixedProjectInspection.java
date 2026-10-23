package dev.ward.domain;

import dev.ward.domain.project.DiscoveredProject;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.ProjectInspection;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** In-memory inspection that detects and discovers one fixed project from any directory. */
public final class FixedProjectInspection implements ProjectInspection {
  private final Project project;

  /** Creates the inspection; every lookup returns {@code project}. */
  public FixedProjectInspection(final Project project) {
    this.project = project;
  }

  @Override
  public Optional<Project> detect(final Path start) {
    return Optional.of(project);
  }

  @Override
  public List<DiscoveredProject> discover(final Path start) {
    return List.of(new DiscoveredProject(project, 0));
  }
}
