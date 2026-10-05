package dev.sentinel.domain.doctor;

import java.nio.file.Path;

/** Outbound contract for observing host and project environment facts. */
public interface EnvironmentInspection {
  EnvironmentFacts inspect(Path projectRoot);
}
