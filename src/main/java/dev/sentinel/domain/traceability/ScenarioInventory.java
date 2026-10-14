package dev.sentinel.domain.traceability;

import java.nio.file.Path;

/** Outbound port for live OpenSpec scenarios. */
@FunctionalInterface
public interface ScenarioInventory {
  TraceabilityInventory<ScenarioDefinition> inspect(Path projectRoot);
}
