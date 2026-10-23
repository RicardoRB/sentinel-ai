package dev.ward.application.gate;

import dev.ward.domain.traceability.ScenarioInventory;
import dev.ward.domain.traceability.TestMappingInventory;

/**
 * The inventories the traceability gate reads.
 *
 * @param scenarios the live OpenSpec scenarios
 * @param mappings the JUnit mappings to those scenarios
 */
public record TraceabilitySources(ScenarioInventory scenarios, TestMappingInventory mappings) {}
