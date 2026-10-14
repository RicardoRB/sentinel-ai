package dev.sentinel.domain.traceability;

import java.nio.file.Path;

/** Read-only prerequisite facts for doctor diagnostics. */
public interface TraceabilityPrerequisites {
  boolean liveSpecsDirectory(Path projectRoot);

  boolean javaTestSourceDirectory(Path projectRoot);
}
