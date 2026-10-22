package dev.ward.config;

import dagger.Binds;
import dagger.Module;
import dev.ward.domain.traceability.ScenarioInventory;
import dev.ward.domain.traceability.TestMappingInventory;
import dev.ward.domain.traceability.TraceabilityPrerequisites;
import dev.ward.infrastructure.traceability.FileSystemScenarioInventory;
import dev.ward.infrastructure.traceability.FileSystemTraceabilityPrerequisites;
import dev.ward.infrastructure.traceability.JavaTestMappingInventory;

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
