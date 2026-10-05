package dev.sentinel.domain.project;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Outbound contract for detecting and discovering project roots. */
public interface ProjectInspection {
  Optional<Project> detect(Path start);

  List<Project> discover(Path start);
}
