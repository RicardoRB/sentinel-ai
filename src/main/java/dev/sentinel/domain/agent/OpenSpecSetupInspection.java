package dev.sentinel.domain.agent;

import java.nio.file.Path;

/** Observes OpenSpec prerequisites without installing, configuring, or running OpenSpec. */
@FunctionalInterface
public interface OpenSpecSetupInspection {
  OpenSpecSetup inspect(Path projectRoot, OpenSpecGuidanceLayout layout);
}
