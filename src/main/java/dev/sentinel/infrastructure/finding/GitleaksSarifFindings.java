package dev.sentinel.infrastructure.finding;

import dev.sentinel.domain.gate.Finding;
import dev.sentinel.domain.gate.FindingExtractionException;
import dev.sentinel.domain.gate.FindingSeverity;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.ToolReports;
import dev.sentinel.domain.json.JsonCodec;
import dev.sentinel.domain.json.JsonCodecException;
import dev.sentinel.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import javax.inject.Inject;
import org.apache.fory.json.annotation.JsonType;

/**
 * Reads the SARIF report written by gitleaks.
 *
 * <p>Only rule ids and locations are mapped. Messages, snippets, fingerprints and properties can
 * carry the matched secret, so they are never read into the model.
 */
public final class GitleaksSarifFindings implements GateFindingSource {
  static final String GATE = "gitleaks";

  private final JsonCodec codec;

  @Inject
  public GitleaksSarifFindings(final JsonCodec codec) {
    this.codec = codec;
  }

  @Override
  public String gateId() {
    return GATE;
  }

  @Override
  public List<Finding> extract(
      final Project project, final GateResult result, final Instant gateStart) {
    return ReportFiles.fresh(
            GATE, gateStart, List.of(project.root().resolve(ToolReports.gitleaksSarif(project))))
        .stream()
        .flatMap(report -> read(report).results().stream())
        .map(sarifResult -> finding(project, sarifResult))
        .toList();
  }

  private SarifLog read(final Path report) {
    try {
      final SarifLog log = codec.fromJson(Files.readString(report), SarifLog.class);
      return log == null ? new SarifLog() : log;
    } catch (IOException exception) {
      throw new FindingExtractionException("Cannot read report", exception);
    } catch (JsonCodecException exception) {
      throw new MalformedReportException(report, exception);
    }
  }

  private static Finding finding(final Project project, final SarifResult result) {
    final String ruleId = result.rule();
    final PhysicalLocation location = result.firstLocation();
    return new Finding(
        GATE,
        GATE,
        ruleId,
        FindingSeverity.ERROR,
        "Potential secret detected by rule " + ruleId + ". Remove it and rotate the credential.",
        ReportFiles.relativize(project, location.uri()),
        location.line(),
        location.column(),
        null);
  }

  private static Integer positive(final Integer value) {
    return value == null || value < 1 ? null : value;
  }

  @JsonType
  static final class SarifLog {
    List<SarifRun> runs;

    List<SarifResult> results() {
      if (runs == null) {
        return List.of();
      }
      return runs.stream()
          .filter(run -> run != null && run.results != null)
          .flatMap(run -> run.results.stream())
          .filter(Objects::nonNull)
          .toList();
    }
  }

  @JsonType
  static final class SarifRun {
    List<SarifResult> results;
  }

  @JsonType
  static final class SarifResult {
    private static final PhysicalLocation NONE = new PhysicalLocation();

    String ruleId;
    List<Location> locations;

    String rule() {
      return ruleId == null || ruleId.isBlank() ? GATE : ruleId.strip();
    }

    PhysicalLocation firstLocation() {
      if (locations == null || locations.isEmpty() || locations.get(0) == null) {
        return NONE;
      }
      final PhysicalLocation physical = locations.get(0).physicalLocation;
      return physical == null ? NONE : physical;
    }
  }

  @JsonType
  static final class Location {
    PhysicalLocation physicalLocation;
  }

  @JsonType
  static final class PhysicalLocation {
    ArtifactLocation artifactLocation;
    Region region;

    String uri() {
      return artifactLocation == null ? null : artifactLocation.uri;
    }

    Integer line() {
      return region == null ? null : positive(region.startLine);
    }

    Integer column() {
      return region == null ? null : positive(region.startColumn);
    }
  }

  @JsonType
  static final class ArtifactLocation {
    String uri;
  }

  @JsonType
  static final class Region {
    Integer startLine;
    Integer startColumn;
  }
}
