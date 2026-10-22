package dev.ward.infrastructure.finding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.ToolReports;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitleaksSarifFindingsTest {
  // Region of the secret in the SARIF fixture below.
  private static final int SECRET_LINE = 3;
  private static final int SECRET_COLUMN = 9;
  static final String SECRET = "ghp_FAKEFAKEFAKEFAKEFAKEFAKEFAKEFAKE1234";

  static final String REPORT =
      """
      {
        "$schema": "https://json.schemastore.org/sarif-2.1.0.json",
        "version": "2.1.0",
        "runs": [{
          "tool": {"driver": {"name": "gitleaks", "rules": [{"id": "github-pat"}]}},
          "results": [{
            "message": {"text": "github-pat has detected secret for file config/app.yml."},
            "ruleId": "github-pat",
            "locations": [{"physicalLocation": {
              "artifactLocation": {"uri": "config/app.yml"},
              "region": {"startLine": 3, "startColumn": 9, "endLine": 3,
                         "snippet": {"text": "token: %1$s"}}
            }}],
            "partialFingerprints": {"commitSha": "", "email": "", "author": ""},
            "properties": {"secret": "%1$s", "match": "token: %1$s"}
          }, {
            "ruleId": " ",
            "locations": []
          }]
        }]
      }
      """
          .formatted(SECRET);

  @TempDir Path root;

  private final GitleaksSarifFindings source = new GitleaksSarifFindings(new ForyJsonCodec());

  @Test
  void mapsRuleAndLocationWithoutTheSecret() {
    FindingFixtures.write(root.resolve("target/gitleaks.sarif"), REPORT);

    final List<Finding> findings =
        source.extract(
            FindingFixtures.maven(root),
            FindingFixtures.failed("gitleaks", ""),
            FindingFixtures.justBefore());

    assertThat(findings).hasSize(2);
    final Finding first = findings.getFirst();
    assertThat(first.ruleId()).isEqualTo("github-pat");
    assertThat(first.file()).isEqualTo("config/app.yml");
    assertThat(first.line()).isEqualTo(SECRET_LINE);
    assertThat(first.column()).isEqualTo(SECRET_COLUMN);
    assertThat(first.severity()).isEqualTo(FindingSeverity.ERROR);
    assertThat(findings.get(1).ruleId()).isEqualTo("gitleaks");
    assertThat(findings.get(1).located()).isFalse();
    assertThat(findings)
        .allSatisfy(finding -> assertThat(finding.toString()).doesNotContain(SECRET));
  }

  @Test
  void gradleReportLivesInBuildDirectory() {
    FindingFixtures.write(root.resolve("build/gitleaks.sarif"), REPORT);

    assertThat(
            source.extract(
                FindingFixtures.gradle(root),
                FindingFixtures.failed("gitleaks", ""),
                FindingFixtures.justBefore()))
        .hasSize(2);
    assertThat(ToolReports.gitleaksSarif(FindingFixtures.gradle(root)))
        .isEqualTo("build/gitleaks.sarif");
    assertThat(ToolReports.gitleaksSarif(FindingFixtures.maven(root)))
        .isEqualTo("target/gitleaks.sarif");
  }

  @Test
  void legacyCommandWithoutReportProducesNoFindings() {
    assertThat(
            source.extract(
                FindingFixtures.maven(root),
                FindingFixtures.failed("gitleaks", "leaks found: 1"),
                FindingFixtures.justBefore()))
        .isEmpty();
  }

  @Test
  void staleReportIsIgnored() {
    FindingFixtures.age(
        FindingFixtures.write(root.resolve("target/gitleaks.sarif"), REPORT), Duration.ofHours(2));

    assertThat(
            source.extract(
                FindingFixtures.maven(root),
                FindingFixtures.failed("gitleaks", ""),
                FindingFixtures.justBefore()))
        .isEmpty();
  }

  @Test
  void malformedReportIsRejected() {
    FindingFixtures.write(root.resolve("target/gitleaks.sarif"), "{\"runs\": [");

    assertThatThrownBy(
            () ->
                source.extract(
                    FindingFixtures.maven(root),
                    FindingFixtures.failed("gitleaks", ""),
                    FindingFixtures.justBefore()))
        .isInstanceOf(MalformedReportException.class)
        .hasMessageNotContaining(SECRET);
  }

  @Test
  void emptyRunsProduceNoFindings() {
    FindingFixtures.write(
        root.resolve("target/gitleaks.sarif"), "{\"runs\": [{\"results\": null}, null]}");

    assertThat(
            source.extract(
                FindingFixtures.maven(root),
                FindingFixtures.failed("gitleaks", ""),
                FindingFixtures.justBefore()))
        .isEmpty();
  }
}
