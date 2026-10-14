package dev.sentinel.config;

import dagger.Binds;
import dagger.Module;
import dev.sentinel.domain.traceability.ScenarioInventory;
import dev.sentinel.domain.traceability.TestMappingInventory;
import dev.sentinel.domain.traceability.TraceabilityPrerequisites;
import dev.sentinel.infrastructure.traceability.FileSystemScenarioInventory;
import dev.sentinel.infrastructure.traceability.FileSystemTraceabilityPrerequisites;
import dev.sentinel.infrastructure.traceability.JavaTestMappingInventory;

@Module
interface TraceabilityBindingsModule {
  @Binds
  ScenarioInventory scenarioInventory(FileSystemScenarioInventory implementation);

  @Binds
  TestMappingInventory testMappingInventory(JavaTestMappingInventory implementation);

  @Binds
  TraceabilityPrerequisites traceabilityPrerequisites(
      FileSystemTraceabilityPrerequisites implementation);
}
