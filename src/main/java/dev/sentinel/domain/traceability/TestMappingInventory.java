package dev.sentinel.domain.traceability;

import java.nio.file.Path;

/** Outbound port for source-level Java test mappings. */
@FunctionalInterface
public interface TestMappingInventory {
  TraceabilityInventory<MappingObservation> inspect(Path projectRoot);
}
