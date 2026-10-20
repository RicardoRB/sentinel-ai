package dev.sentinel.domain.traceability;

import java.nio.file.Path;

/** Outbound port for source-level Java test mappings. */
@FunctionalInterface
public interface TestMappingInventory {
  /**
   * Lists the scenario annotations in the Java tests under {@code projectRoot}, with any files that
   * could not be read.
   */
  TraceabilityInventory<MappingObservation> inspect(Path projectRoot);
}
