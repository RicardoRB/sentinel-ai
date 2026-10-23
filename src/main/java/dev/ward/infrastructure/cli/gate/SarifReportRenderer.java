package dev.ward.infrastructure.cli.gate;

import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateFindings;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.SeverityThreshold;
import dev.ward.domain.json.JsonCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.fory.json.annotation.JsonProperty;
import org.apache.fory.json.annotation.JsonType;

/** Renders a check report as a SARIF 2.1.0 log with one run per executed gate. */
public class SarifReportRenderer {
  static final String SCHEMA = "https://json.schemastore.org/sarif-2.1.0.json";
  static final String VERSION = "2.1.0";
  private static final Map<String, String> TOOLS = Map.of("compile", "javac", "format", "spotless");

  private final JsonCodec codec;

  /** Creates the renderer; {@code codec} serializes the SARIF DTOs. */
  @Inject
  public SarifReportRenderer(final JsonCodec codec) {
    this.codec = codec;
  }

  /** Renders {@code report} as a SARIF 2.1.0 log with warning-level findings and above. */
  public String render(final CheckReport report) {
    return render(report, SeverityThreshold.WARNING);
  }

  /**
   * Renders {@code report} as a SARIF 2.1.0 log with one run per executed gate, listing findings at
   * or above {@code threshold}.
   */
  public String render(final CheckReport report, final SeverityThreshold threshold) {
    final List<RunDto> runs = new ArrayList<>();
    // Code scanning resolves relative URIs against the repository root, not the project root.
    final String prefix = report.project().nested() ? report.project().projectPath() + "/" : "";
    for (final GateResult result : report.results()) {
      if (result.status() != GateStatus.SKIPPED) {
        runs.add(run(result, threshold, prefix));
      }
    }
    return codec.toJson(new LogDto(SCHEMA, VERSION, runs));
  }

  private static RunDto run(
      final GateResult result, final SeverityThreshold threshold, final String prefix) {
    final GateFindings.Visible visible = result.findings().visible(threshold);
    final List<Finding> findings = visible.items();
    final Map<String, RuleDto> rules =
        findings.stream()
            .collect(
                Collectors.toMap(
                    Finding::ruleId,
                    finding -> new RuleDto(finding.ruleId(), finding.helpUri()),
                    (first, ignored) -> first,
                    LinkedHashMap::new));
    final List<ResultDto> results =
        findings.stream()
            .map(finding -> ResultDto.from(finding, prefix))
            .collect(Collectors.toCollection(ArrayList::new));
    if (!result.passed() && findings.isEmpty()) {
      rules.putIfAbsent(result.name(), new RuleDto(result.name(), null));
      results.add(
          new ResultDto(
              result.name(),
              "error",
              new MessageDto(
                  "Quality gate '"
                      + result.name()
                      + "' did not pass (status "
                      + result.status()
                      + ")."),
              List.of()));
    }
    final DriverDto driver =
        new DriverDto(
            TOOLS.getOrDefault(result.name(), result.name()), List.copyOf(rules.values()));
    return new RunDto(
        new ToolDto(driver),
        results,
        new RunPropertiesDto(
            result.name(),
            result.status().name(),
            visible.total(),
            visible.hidden(),
            visible.truncated()));
  }

  /**
   * The SARIF log root.
   *
   * @param schema the SARIF JSON schema URI
   * @param version the SARIF version
   * @param runs one run per executed gate
   */
  @JsonType
  public record LogDto(@JsonProperty("$schema") String schema, String version, List<RunDto> runs) {
    /** Copies the runs. */
    public LogDto {
      runs = List.copyOf(runs);
    }
  }

  /**
   * One gate's SARIF run.
   *
   * @param tool the tool that produced the results
   * @param results the visible results
   * @param properties gate status and truncation details
   */
  @JsonType
  public record RunDto(ToolDto tool, List<ResultDto> results, RunPropertiesDto properties) {
    /** Copies the results. */
    public RunDto {
      results = List.copyOf(results);
    }
  }

  /**
   * The SARIF tool object.
   *
   * @param driver the tool component
   */
  @JsonType
  public record ToolDto(DriverDto driver) {}

  /**
   * The SARIF tool component.
   *
   * @param name the tool name
   * @param rules the rules referenced by results
   */
  @JsonType
  public record DriverDto(String name, List<RuleDto> rules) {
    /** Copies the rules. */
    public DriverDto {
      rules = List.copyOf(rules);
    }
  }

  /**
   * A SARIF rule descriptor.
   *
   * @param id the rule ID
   * @param helpUri documentation for the rule, when known
   */
  @JsonType
  public record RuleDto(String id, String helpUri) {}

  /**
   * Ward properties of a run.
   *
   * @param gate the gate ID
   * @param status the gate status
   * @param findingsTotal findings the tool reported
   * @param findingsHidden findings below the severity threshold
   * @param findingsTruncated whether the result list was capped
   */
  @JsonType
  public record RunPropertiesDto(
      String gate,
      String status,
      int findingsTotal,
      int findingsHidden,
      boolean findingsTruncated) {}

  /**
   * A SARIF message.
   *
   * @param text the message text
   */
  @JsonType
  public record MessageDto(String text) {}

  /**
   * One SARIF result.
   *
   * @param ruleId the rule that produced it
   * @param level the SARIF level
   * @param message the result message
   * @param locations where it applies; empty for gate-level results
   */
  @JsonType
  public record ResultDto(
      String ruleId, String level, MessageDto message, List<LocationDto> locations) {
    /** Copies the locations. */
    public ResultDto {
      locations = List.copyOf(locations);
    }

    static ResultDto from(final Finding finding, final String prefix) {
      final List<LocationDto> locations =
          finding.located()
              ? List.of(
                  new LocationDto(
                      new PhysicalLocationDto(
                          new ArtifactLocationDto(uri(finding.file(), prefix)),
                          finding.line() == null
                              ? null
                              : new RegionDto(finding.line(), finding.column()))))
              : List.of();
      return new ResultDto(
          finding.ruleId(),
          finding.severity().label(),
          new MessageDto(finding.message()),
          locations);
    }

    private static String uri(final String file, final String prefix) {
      return file.startsWith("/") ? "file://" + file : prefix + file;
    }
  }

  /**
   * A SARIF location.
   *
   * @param physicalLocation the file and region
   */
  @JsonType
  public record LocationDto(PhysicalLocationDto physicalLocation) {}

  /**
   * A SARIF physical location.
   *
   * @param artifactLocation the file
   * @param region the line and column, or {@code null} for file-level results
   */
  @JsonType
  public record PhysicalLocationDto(ArtifactLocationDto artifactLocation, RegionDto region) {}

  /**
   * A SARIF artifact location.
   *
   * @param uri a repository-relative path, or a file URI outside the project
   */
  @JsonType
  public record ArtifactLocationDto(String uri) {}

  /**
   * A SARIF region.
   *
   * @param startLine the 1-based line, when known
   * @param startColumn the 1-based column, when known
   */
  @JsonType
  public record RegionDto(Integer startLine, Integer startColumn) {}
}
