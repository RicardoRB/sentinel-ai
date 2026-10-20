package dev.sentinel.domain.traceability;

import java.nio.file.Path;

/** Outbound port for live OpenSpec scenarios. */
@FunctionalInterface
public interface ScenarioInventory {
  /** Lists the live scenarios under {@code projectRoot}, with any files that could not be read. */
  TraceabilityInventory<ScenarioDefinition> inspect(Path projectRoot);
}
