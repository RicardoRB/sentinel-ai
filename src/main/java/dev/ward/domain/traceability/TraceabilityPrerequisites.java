package dev.ward.domain.traceability;

import java.nio.file.Path;

/** Read-only prerequisite facts for doctor diagnostics. */
public interface TraceabilityPrerequisites {
  /** Returns whether {@code projectRoot} has an {@code openspec/specs} directory. */
  boolean liveSpecsDirectory(Path projectRoot);

  /** Returns whether {@code projectRoot} has Java test sources. */
  boolean javaTestSourceDirectory(Path projectRoot);
}
