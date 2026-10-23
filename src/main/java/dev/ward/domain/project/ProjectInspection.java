package dev.ward.domain.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Outbound contract for detecting and discovering project roots. */
public interface ProjectInspection {
  /** Returns the project containing {@code start}, if it is supported. */
  Optional<Project> detect(Path start);

  /**
   * Returns every supported project under the repository containing {@code start}, with build
   * modules grouped under their aggregator.
   */
  List<DiscoveredProject> discover(Path start);
}
