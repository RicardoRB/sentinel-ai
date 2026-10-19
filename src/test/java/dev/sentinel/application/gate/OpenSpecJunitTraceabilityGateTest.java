package dev.sentinel.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import dev.sentinel.domain.traceability.MappingObservation;
import dev.sentinel.domain.traceability.ScenarioDefinition;
import dev.sentinel.domain.traceability.ScenarioIdentity;
import dev.sentinel.domain.traceability.SourceLocation;
import dev.sentinel.domain.traceability.TraceabilityInventory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenSpecJunitTraceabilityGateTest {
  private static final Path ROOT = Path.of("/project");
  private static final Project PROJECT =
      new Project(ROOT, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  private static final Path SPEC = Path.of("/project/openspec/specs/auth/spec.md");
  private static final Path TEST = Path.of("/project/src/test/java/AuthTest.java");
  private static final ScenarioDefinition SIGN_IN =
      new ScenarioDefinition(
          new ScenarioIdentity("auth", "Successful sign-in"), new SourceLocation(SPEC, 2));

  @Test
  void coveredScenariosPassAndInspectTheProjectRoot() {
    final List<Path> inspectedRoots = new ArrayList<>();
    final OpenSpecJunitTraceabilityGate gate =
        new OpenSpecJunitTraceabilityGate(
            root -> {
              inspectedRoots.add(root);
              return new TraceabilityInventory<>(List.of(SIGN_IN), List.of());
            },
            root -> {
              inspectedRoots.add(root);
              return new TraceabilityInventory<>(
                  List.of(
                      new MappingObservation(
                          "auth", "Successful sign-in", new SourceLocation(TEST, 7), true, "")),
                  List.of());
            });

    final GateResult result = gate.execute(PROJECT);

    assertThat(gate.name()).isEqualTo("openspec-junit-traceability");
    assertThat(inspectedRoots).containsExactly(ROOT, ROOT);
    assertThat(result.name()).isEqualTo(OpenSpecJunitTraceabilityGate.NAME);
    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(result.exitCode()).isZero();
    assertThat(result.command()).isEmpty();
    assertThat(result.summary()).isEqualTo("all scenarios covered");
    assertThat(result.stderr()).isEqualTo("all scenarios covered");
    assertThat(result.errors()).isZero();
  }

  @Test
  void missingMappingFailsAndRendersTheFindingWithItsLocation() {
    final OpenSpecJunitTraceabilityGate gate =
        new OpenSpecJunitTraceabilityGate(
            root -> new TraceabilityInventory<>(List.of(SIGN_IN), List.of()),
            root -> new TraceabilityInventory<>(List.of(), List.of()));

    final GateResult result = gate.execute(PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(result.summary()).isEqualTo("traceability mappings are incomplete");
    assertThat(result.errors()).isEqualTo(1);
    assertThat(result.stderr())
        .isEqualTo(
            "traceability mappings are incomplete"
                + System.lineSeparator()
                + "MISSING: missing mapping for "
                + SIGN_IN.identity()
                + " at "
                + SPEC
                + ":2");
  }

  @Test
  void unreadableTestSourcesFailWithoutALocation() {
    final OpenSpecJunitTraceabilityGate gate =
        new OpenSpecJunitTraceabilityGate(
            root -> new TraceabilityInventory<>(List.of(), List.of()),
            root -> new TraceabilityInventory<>(List.of(), List.of("cannot read AuthTest.java")));

    final GateResult result = gate.execute(PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).isEqualTo("no scenarios found");
    assertThat(result.stderr())
        .isEqualTo(
            "no scenarios found"
                + System.lineSeparator()
                + "UNREADABLE: cannot read AuthTest.java");
  }
}
