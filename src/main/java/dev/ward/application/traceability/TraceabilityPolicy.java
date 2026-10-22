package dev.ward.application.traceability;

import dev.ward.domain.traceability.MappingObservation;
import dev.ward.domain.traceability.ScenarioDefinition;
import dev.ward.domain.traceability.ScenarioIdentity;
import dev.ward.domain.traceability.TraceabilityFinding;
import dev.ward.domain.traceability.TraceabilityInventory;
import dev.ward.domain.traceability.TraceabilityResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure policy for deciding mapping coverage from two immutable inventories. */
public final class TraceabilityPolicy {
  private static final int MULTIPLE_MATCHES = 2;

  /**
   * Compares the live scenarios with the test mappings and reports missing, ambiguous, malformed,
   * unknown, and unreadable entries.
   */
  public TraceabilityResult evaluate(
      final TraceabilityInventory<ScenarioDefinition> scenarios,
      final TraceabilityInventory<MappingObservation> mappings) {
    final List<TraceabilityFinding> findings = new ArrayList<>();
    mappings.failures().stream()
        .sorted()
        .forEach(
            failure ->
                findings.add(
                    new TraceabilityFinding(TraceabilityFinding.Kind.UNREADABLE, failure, null)));
    if (scenarios.entries().isEmpty()) {
      return new TraceabilityResult(
          TraceabilityResult.Status.NO_SCENARIOS, "no scenarios found", findings);
    }
    final Map<ScenarioIdentity, List<MappingObservation>> byIdentity =
        addMappings(mappings, findings);
    addScenarioFindings(scenarios, byIdentity, findings);
    addUnknownFindings(scenarios, mappings, findings);
    findings.sort(
        Comparator.comparing(
                TraceabilityFinding::location, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(TraceabilityFinding::message));
    if (findings.isEmpty()) {
      return new TraceabilityResult(
          TraceabilityResult.Status.PASSED, "all scenarios covered", findings);
    }
    return new TraceabilityResult(
        TraceabilityResult.Status.FAILED, "traceability mappings are incomplete", findings);
  }

  private static Map<ScenarioIdentity, List<MappingObservation>> addMappings(
      final TraceabilityInventory<MappingObservation> mappings,
      final List<TraceabilityFinding> findings) {
    final Map<ScenarioIdentity, List<MappingObservation>> byIdentity = new HashMap<>();
    for (final MappingObservation mapping : mappings.entries()) {
      if (!mapping.validShape()) {
        final String message =
            mapping.problem().isBlank() ? "malformed mapping" : mapping.problem();
        findings.add(
            new TraceabilityFinding(
                TraceabilityFinding.Kind.MALFORMED, message, mapping.location()));
        continue;
      }
      final List<MappingObservation> entries = byIdentity.get(mapping.identity());
      if (entries == null) {
        final List<MappingObservation> newEntries = newEntries();
        byIdentity.put(mapping.identity(), newEntries);
        newEntries.add(mapping);
        continue;
      }
      entries.add(mapping);
    }
    return byIdentity;
  }

  private static List<MappingObservation> newEntries() {
    return new ArrayList<>();
  }

  private static void addScenarioFindings(
      final TraceabilityInventory<ScenarioDefinition> scenarios,
      final Map<ScenarioIdentity, List<MappingObservation>> byIdentity,
      final List<TraceabilityFinding> findings) {
    for (final ScenarioDefinition scenario : scenarios.entries().stream().sorted().toList()) {
      final List<MappingObservation> matches =
          byIdentity.getOrDefault(scenario.identity(), List.of());
      if (matches.isEmpty()) {
        findings.add(
            new TraceabilityFinding(
                TraceabilityFinding.Kind.MISSING,
                "missing mapping for " + scenario.identity(),
                scenario.location()));
      } else if (matches.size() >= MULTIPLE_MATCHES) {
        findings.add(
            new TraceabilityFinding(
                TraceabilityFinding.Kind.AMBIGUOUS,
                "ambiguous mapping for " + scenario.identity() + " (" + matches.size() + " tests)",
                matches.stream()
                    .map(MappingObservation::location)
                    .min(Comparator.naturalOrder())
                    .orElse(null)));
      }
    }
  }

  private static void addUnknownFindings(
      final TraceabilityInventory<ScenarioDefinition> scenarios,
      final TraceabilityInventory<MappingObservation> mappings,
      final List<TraceabilityFinding> findings) {
    final List<ScenarioIdentity> known =
        scenarios.entries().stream().map(ScenarioDefinition::identity).toList();
    for (final MappingObservation mapping : mappings.entries()) {
      if (mapping.validShape() && !known.contains(mapping.identity())) {
        findings.add(
            new TraceabilityFinding(
                TraceabilityFinding.Kind.UNKNOWN,
                "unknown scenario mapping " + mapping.identity(),
                mapping.location()));
      }
    }
  }
}
