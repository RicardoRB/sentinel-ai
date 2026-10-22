package dev.ward.application.gate;

import dev.ward.application.traceability.TraceabilityPolicy;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.project.Project;
import dev.ward.domain.traceability.MappingObservation;
import dev.ward.domain.traceability.ScenarioDefinition;
import dev.ward.domain.traceability.ScenarioInventory;
import dev.ward.domain.traceability.TestMappingInventory;
import dev.ward.domain.traceability.TraceabilityFinding;
import dev.ward.domain.traceability.TraceabilityInventory;
import dev.ward.domain.traceability.TraceabilityResult;
import java.time.Duration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Opt-in source-level mapping evidence; this gate never executes JUnit. */
public final class OpenSpecJunitTraceabilityGate implements QualityGate {
  public static final String NAME = "openspec-junit-traceability";
  private static final Logger LOGGER =
      Logger.getLogger(OpenSpecJunitTraceabilityGate.class.getName());
  private final ScenarioInventory scenarios;
  private final TestMappingInventory mappings;
  private final TraceabilityPolicy policy;

  /** Creates the gate with the default mapping policy. */
  public OpenSpecJunitTraceabilityGate(
      final ScenarioInventory scenarios, final TestMappingInventory mappings) {
    this(scenarios, mappings, new TraceabilityPolicy());
  }

  /**
   * Creates the gate. {@code scenarios} lists the live OpenSpec scenarios, {@code mappings} the
   * annotated tests, and {@code policy} decides coverage.
   */
  public OpenSpecJunitTraceabilityGate(
      final ScenarioInventory scenarios,
      final TestMappingInventory mappings,
      final TraceabilityPolicy policy) {
    this.scenarios = scenarios;
    this.mappings = mappings;
    this.policy = policy;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public GateResult execute(final Project project) {
    final TraceabilityInventory<ScenarioDefinition> scenarioInventory =
        scenarios.inspect(project.root());
    final TraceabilityInventory<MappingObservation> mappingInventory =
        mappings.inspect(project.root());
    LOGGER.log(
        Level.INFO,
        () ->
            "event=traceability-inventory scenarios="
                + scenarioInventory.entries().size()
                + " mappings="
                + mappingInventory.entries().size());
    final TraceabilityResult result = policy.evaluate(scenarioInventory, mappingInventory);
    LOGGER.log(
        Level.INFO,
        () ->
            "event=traceability-decision status="
                + result.status()
                + " findings="
                + result.findings().size());
    final String detail = render(result);
    final GateStatus status = result.passed() ? GateStatus.PASSED : GateStatus.FAILED;
    return GateResult.builder(NAME, status)
        .command(List.of())
        .exitCode(result.passed() ? 0 : 1)
        .duration(Duration.ZERO)
        .stdout("")
        .stderr(detail)
        .summary(result.summary())
        .errors(result.findings().size())
        .warnings(0)
        .build();
  }

  private static String render(final TraceabilityResult result) {
    final StringBuilder output = new StringBuilder(result.summary());
    for (final TraceabilityFinding finding : result.findings()) {
      output
          .append(System.lineSeparator())
          .append(finding.kind())
          .append(": ")
          .append(finding.message());
      if (finding.location() != null) {
        output
            .append(" at ")
            .append(finding.location().path())
            .append(':')
            .append(finding.location().line());
      }
    }
    return output.toString();
  }
}
