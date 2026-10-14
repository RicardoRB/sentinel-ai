package dev.sentinel.application.traceability;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.traceability.MappingObservation;
import dev.sentinel.domain.traceability.ScenarioDefinition;
import dev.sentinel.domain.traceability.ScenarioIdentity;
import dev.sentinel.domain.traceability.SourceLocation;
import dev.sentinel.domain.traceability.TraceabilityFinding;
import dev.sentinel.domain.traceability.TraceabilityInventory;
import dev.sentinel.domain.traceability.TraceabilityResult;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TraceabilityPolicyTest {
  private static final Path SPEC = Path.of("spec.md");
  private static final Path TEST = Path.of("Test.java");

  @Test
  void completeNonEmptyInventoryPasses() {
    final ScenarioDefinition scenario = scenario("identity/user-auth", "Successful sign-in");
    final TraceabilityResult result =
        new TraceabilityPolicy()
            .evaluate(
                new TraceabilityInventory<>(List.of(scenario), List.of()),
                new TraceabilityInventory<>(
                    List.of(
                        new MappingObservation(
                            "identity/user-auth",
                            "Successful sign-in",
                            new SourceLocation(TEST, 4),
                            true,
                            "")),
                    List.of()));
    assertThat(result.status()).isEqualTo(TraceabilityResult.Status.PASSED);
  }

  @Test
  void missingAndAmbiguousMappingsFailWithKinds() {
    final ScenarioDefinition scenario = scenario("identity/user-auth", "Successful sign-in");
    final MappingObservation mapping =
        new MappingObservation(
            "identity/user-auth", "Successful sign-in", new SourceLocation(TEST, 4), true, "");
    final TraceabilityResult result =
        new TraceabilityPolicy()
            .evaluate(
                new TraceabilityInventory<>(List.of(scenario), List.of()),
                new TraceabilityInventory<>(List.of(mapping, mapping), List.of()));
    assertThat(result.findings())
        .extracting(TraceabilityFinding::kind)
        .containsExactly(TraceabilityFinding.Kind.AMBIGUOUS);
  }

  @Test
  void emptyInventoryIsNotApass() {
    final TraceabilityResult result =
        new TraceabilityPolicy()
            .evaluate(
                new TraceabilityInventory<>(List.of(), List.of()),
                new TraceabilityInventory<>(List.of(), List.of()));
    assertThat(result.status()).isEqualTo(TraceabilityResult.Status.NO_SCENARIOS);
    assertThat(result.summary()).isEqualTo("no scenarios found");
  }

  private static ScenarioDefinition scenario(final String capability, final String name) {
    return new ScenarioDefinition(
        new ScenarioIdentity(capability, name), new SourceLocation(SPEC, 2));
  }
}
