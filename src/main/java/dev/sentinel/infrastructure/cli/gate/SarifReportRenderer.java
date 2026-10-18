package dev.sentinel.infrastructure.cli.gate;

import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.Finding;
import dev.sentinel.domain.gate.GateFindings;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.gate.SeverityThreshold;
import dev.sentinel.domain.json.JsonCodec;
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

  @Inject
  public SarifReportRenderer(final JsonCodec codec) {
    this.codec = codec;
  }

  public String render(final CheckReport report) {
    return render(report, SeverityThreshold.WARNING);
  }

  public String render(final CheckReport report, final SeverityThreshold threshold) {
    final List<RunDto> runs = new ArrayList<>();
    for (final GateResult result : report.results()) {
      if (result.status() != GateStatus.SKIPPED) {
        runs.add(run(result, threshold));
      }
    }
    return codec.toPrettyJson(new LogDto(SCHEMA, VERSION, runs));
  }

  private static RunDto run(final GateResult result, final SeverityThreshold threshold) {
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
        findings.stream().map(ResultDto::from).collect(Collectors.toCollection(ArrayList::new));
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

  @JsonType
  public record LogDto(@JsonProperty("$schema") String schema, String version, List<RunDto> runs) {
    public LogDto {
      runs = List.copyOf(runs);
    }
  }

  @JsonType
  public record RunDto(ToolDto tool, List<ResultDto> results, RunPropertiesDto properties) {
    public RunDto {
      results = List.copyOf(results);
    }
  }

  @JsonType
  public record ToolDto(DriverDto driver) {}

  @JsonType
  public record DriverDto(String name, List<RuleDto> rules) {
    public DriverDto {
      rules = List.copyOf(rules);
    }
  }

  @JsonType
  public record RuleDto(String id, String helpUri) {}

  @JsonType
  public record RunPropertiesDto(
      String gate,
      String status,
      int findingsTotal,
      int findingsHidden,
      boolean findingsTruncated) {}

  @JsonType
  public record MessageDto(String text) {}

  @JsonType
  public record ResultDto(
      String ruleId, String level, MessageDto message, List<LocationDto> locations) {
    public ResultDto {
      locations = List.copyOf(locations);
    }

    static ResultDto from(final Finding finding) {
      final List<LocationDto> locations =
          finding.located()
              ? List.of(
                  new LocationDto(
                      new PhysicalLocationDto(
                          new ArtifactLocationDto(uri(finding.file())),
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

    private static String uri(final String file) {
      return file.startsWith("/") ? "file://" + file : file;
    }
  }

  @JsonType
  public record LocationDto(PhysicalLocationDto physicalLocation) {}

  @JsonType
  public record PhysicalLocationDto(ArtifactLocationDto artifactLocation, RegionDto region) {}

  @JsonType
  public record ArtifactLocationDto(String uri) {}

  @JsonType
  public record RegionDto(Integer startLine, Integer startColumn) {}
}
