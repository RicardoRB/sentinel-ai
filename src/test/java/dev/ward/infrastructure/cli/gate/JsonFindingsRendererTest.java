package dev.ward.infrastructure.cli.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateFindings;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.gate.SeverityThreshold;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class JsonFindingsRendererTest {
  private static final int REPORTED = 120;
  private static final int FINDING_COLUMN = 5;
  private static final int LOG_LINES = 500;
  private final Project project =
      new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);

  private static Finding finding(final int line) {
    return new Finding(
        "checkstyle",
        "checkstyle",
        "MissingJavadocMethod",
        FindingSeverity.ERROR,
        "Missing a Javadoc comment.",
        "src/main/java/Foo.java",
        line,
        FINDING_COLUMN,
        "https://checkstyle.org/checks/javadoc/missingjavadocmethod.html");
  }

  private static GateResult result(final String name, final GateStatus status, final String out) {
    return GateResult.builder(name, status)
        .command(List.of("x"))
        .exitCode(1)
        .duration(Duration.ZERO)
        .stdout(out)
        .stderr("")
        .build();
  }

  private JsonTree render(final GateResult... results) {
    return JsonTree.parse(
        new JsonReportRenderer(new ForyJsonCodec())
            .render(new CheckReport(project, List.of(results))));
  }

  private JsonTree renderInfo(final GateResult... results) {
    return JsonTree.parse(
        new JsonReportRenderer(new ForyJsonCodec())
            .render(
                new CheckReport(project, List.of(results)),
                false,
                false,
                null,
                SeverityThreshold.INFO));
  }

  @Test
  void truncatedGateListsFiftyFindingsWithTotal() {
    final GateFindings findings =
        GateFindings.of(
            IntStream.rangeClosed(1, REPORTED)
                .mapToObj(JsonFindingsRendererTest::finding)
                .toList());

    final JsonTree check =
        render(result("checkstyle", GateStatus.FAILED, "log").withFindings(findings))
            .get("checks")
            .get(0);

    assertThat(check.get("findings").size()).isEqualTo(GateFindings.LIMIT);
    assertThat(check.get("findingsTotal").asInt()).isEqualTo(REPORTED);
    assertThat(check.get("findingsTruncated").asBoolean()).isTrue();
    final JsonTree first = check.get("findings").get(0);
    assertThat(first.get("tool").asString()).isEqualTo("checkstyle");
    assertThat(first.get("rule").asString()).isEqualTo("MissingJavadocMethod");
    assertThat(first.get("severity").asString()).isEqualTo("error");
    assertThat(first.get("message").asString()).isEqualTo("Missing a Javadoc comment.");
    assertThat(first.get("file").asString()).isEqualTo("src/main/java/Foo.java");
    assertThat(first.get("line").asInt()).isEqualTo(1);
    assertThat(first.get("column").asInt()).isEqualTo(FINDING_COLUMN);
    assertThat(first.get("helpUri").asString()).startsWith("https://checkstyle.org/");
  }

  @Test
  void gateWithoutFindingsHasEmptyArray() {
    final JsonTree check = render(result("tests", GateStatus.PASSED, "ok")).get("checks").get(0);

    assertThat(check.get("findings").size()).isZero();
    assertThat(check.get("findingsTotal").asInt()).isZero();
    assertThat(check.get("findingsTruncated").asBoolean()).isFalse();
  }

  @Test
  void outputKeepsTailWhileStdoutStaysComplete() {
    final String log =
        IntStream.rangeClosed(1, LOG_LINES)
            .mapToObj(i -> "line " + i)
            .collect(Collectors.joining("\n"));

    final JsonTree check = renderInfo(result("tests", GateStatus.FAILED, log)).get("checks").get(0);

    final String output = check.get("output").asString();
    assertThat(output.lines()).hasSize(JsonReportRenderer.OUTPUT_TAIL_LINES);
    assertThat(output).startsWith("line 301\n").endsWith("line 500");
    assertThat(check.get("stdout").asString()).isEqualTo(log);
  }

  @Test
  void shortOutputIsUnchanged() {
    assertThat(JsonReportRenderer.CheckDto.tail("a\nb\n")).isEqualTo("a\nb\n");
  }
}
