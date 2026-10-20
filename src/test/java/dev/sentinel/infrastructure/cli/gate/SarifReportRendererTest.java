package dev.sentinel.infrastructure.cli.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.JsonTree;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.Finding;
import dev.sentinel.domain.gate.FindingSeverity;
import dev.sentinel.domain.gate.GateFindings;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import dev.sentinel.infrastructure.json.ForyJsonCodec;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SarifReportRendererTest {
  private static final int UNUSED_VARIABLE_LINE = 10;
  private static final int REPORTED = 120;
  private static final int DISTINCT_RULES = 3;
  private static final int OUTSIDE_LINE = 3;
  private static final int OUTSIDE_COLUMN = 4;
  private static final String RUNS = "runs";
  private static final String RESULTS = "results";

  private final Project project =
      new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);

  private static GateResult result(final String name, final GateStatus status) {
    return GateResult.builder(name, status)
        .command(List.of("x"))
        .exitCode(1)
        .duration(Duration.ZERO)
        .stdout("")
        .stderr("")
        .build();
  }

  private static Finding pmd(final int line, final String rule) {
    return new Finding(
        "pmd",
        "pmd",
        rule,
        FindingSeverity.WARNING,
        "Avoid unused local variables.",
        "src/main/java/Foo.java",
        line,
        null,
        "https://docs.pmd-code.org/" + rule);
  }

  private JsonTree render(final GateResult... results) {
    return JsonTree.parse(
        new SarifReportRenderer(new ForyJsonCodec())
            .render(new CheckReport(project, List.of(results))));
  }

  @Test
  void documentIsSarifWithOneRunPerExecutedGate() {
    final JsonTree log =
        render(
            result("checkstyle", GateStatus.PASSED),
            result("pmd", GateStatus.PASSED),
            result("spotbugs", GateStatus.SKIPPED));

    assertThat(log.get("$schema").asString()).isEqualTo(SarifReportRenderer.SCHEMA);
    assertThat(log.get("version").asString()).isEqualTo("2.1.0");
    assertThat(log.get(RUNS).size()).isEqualTo(2);
    final JsonTree run = log.get(RUNS).get(0);
    assertThat(run.get("tool").get("driver").get("name").asString()).isEqualTo("checkstyle");
    assertThat(run.get(RESULTS).size()).isZero();
    assertThat(run.get("properties").get("status").asString()).isEqualTo("PASSED");
  }

  @Test
  void findingsBecomeLocatedResultsWithDeduplicatedRules() {
    final GateResult pmd =
        result("pmd", GateStatus.FAILED)
            .withFindings(
                GateFindings.of(
                    List.of(
                        pmd(UNUSED_VARIABLE_LINE, "UnusedLocalVariable"),
                        pmd(UNUSED_VARIABLE_LINE + 2, "UnusedLocalVariable"))));

    final JsonTree run = render(pmd).get(RUNS).get(0);

    final JsonTree rules = run.get("tool").get("driver").get("rules");
    assertThat(rules.size()).isEqualTo(1);
    assertThat(rules.get(0).get("id").asString()).isEqualTo("UnusedLocalVariable");
    assertThat(rules.get(0).get("helpUri").asString())
        .isEqualTo("https://docs.pmd-code.org/UnusedLocalVariable");
    final JsonTree first = run.get(RESULTS).get(0);
    assertThat(first.get("ruleId").asString()).isEqualTo("UnusedLocalVariable");
    assertThat(first.get("level").asString()).isEqualTo("warning");
    assertThat(first.get("message").get("text").asString())
        .isEqualTo("Avoid unused local variables.");
    final JsonTree location = first.get("locations").get(0).get("physicalLocation");
    assertThat(location.get("artifactLocation").get("uri").asString())
        .isEqualTo("src/main/java/Foo.java");
    assertThat(location.get("region").get("startLine").asInt()).isEqualTo(UNUSED_VARIABLE_LINE);
    assertThat(location.get("region").has("startColumn")).isFalse();
  }

  @Test
  void failedGateWithoutFindingsGetsOneLocationLessResult() {
    final JsonTree run = render(result("tests", GateStatus.FAILED)).get(RUNS).get(0);

    assertThat(run.get(RESULTS).size()).isEqualTo(1);
    final JsonTree only = run.get(RESULTS).get(0);
    assertThat(only.get("ruleId").asString()).isEqualTo("tests");
    assertThat(only.get("level").asString()).isEqualTo("error");
    assertThat(only.get("message").get("text").asString()).contains("FAILED");
    assertThat(only.get("locations").size()).isZero();
  }

  @Test
  void unavailableGateIsReportedWithItsStatus() {
    final JsonTree only =
        render(result("gitleaks", GateStatus.UNAVAILABLE)).get(RUNS).get(0).get(RESULTS).get(0);

    assertThat(only.get("message").get("text").asString()).contains("UNAVAILABLE");
  }

  @Test
  void truncatedRunRecordsTotal() {
    final GateFindings findings =
        GateFindings.of(
            IntStream.rangeClosed(1, REPORTED)
                .mapToObj(i -> pmd(i, "R" + (i % DISTINCT_RULES)))
                .toList());

    final JsonTree run =
        render(result("pmd", GateStatus.FAILED).withFindings(findings)).get(RUNS).get(0);

    assertThat(run.get(RESULTS).size()).isEqualTo(GateFindings.LIMIT);
    assertThat(run.get("properties").get("findingsTotal").asInt()).isEqualTo(REPORTED);
    assertThat(run.get("properties").get("findingsTruncated").asBoolean()).isTrue();
  }

  @Test
  void fileLevelAndOutsideFindingsAndToolNames() {
    final Finding format =
        new Finding(
            "format", "spotless", "format", FindingSeverity.ERROR, "m", "A.java", null, null, null);
    final Finding outside =
        new Finding(
            "compile",
            "javac",
            "javac.error",
            FindingSeverity.ERROR,
            "m",
            "/x/B.java",
            OUTSIDE_LINE,
            OUTSIDE_COLUMN,
            null);

    final JsonTree log =
        render(
            result("format", GateStatus.FAILED).withFindings(GateFindings.of(List.of(format))),
            result("compile", GateStatus.FAILED).withFindings(GateFindings.of(List.of(outside))));

    final JsonTree formatRun = log.get(RUNS).get(0);
    assertThat(formatRun.get("tool").get("driver").get("name").asString()).isEqualTo("spotless");
    assertThat(
            formatRun
                .get(RESULTS)
                .get(0)
                .get("locations")
                .get(0)
                .get("physicalLocation")
                .has("region"))
        .isFalse();
    final JsonTree compileRun = log.get(RUNS).get(1);
    assertThat(compileRun.get("tool").get("driver").get("name").asString()).isEqualTo("javac");
    final JsonTree physical =
        compileRun.get(RESULTS).get(0).get("locations").get(0).get("physicalLocation");
    assertThat(physical.get("artifactLocation").get("uri").asString())
        .isEqualTo("file:///x/B.java");
    assertThat(physical.get("region").get("startColumn").asInt()).isEqualTo(OUTSIDE_COLUMN);
  }
}
