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
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class JsonFindingsRendererTest {
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
        5,
        "https://checkstyle.org/checks/javadoc/missingjavadocmethod.html");
  }

  private static GateResult result(final String name, final GateStatus status, final String out) {
    return new GateResult(name, status, List.of("x"), 1, Duration.ZERO, out, "");
  }

  private JsonTree render(final GateResult... results) {
    return JsonTree.parse(
        new JsonReportRenderer(new ForyJsonCodec())
            .render(new CheckReport(project, List.of(results))));
  }

  @Test
  void truncatedGateListsFiftyFindingsWithTotal() {
    final GateFindings findings =
        GateFindings.of(
            IntStream.rangeClosed(1, 120).mapToObj(JsonFindingsRendererTest::finding).toList());

    final JsonTree check =
        render(result("checkstyle", GateStatus.FAILED, "log").withFindings(findings))
            .get("checks")
            .get(0);

    assertThat(check.get("findings").size()).isEqualTo(50);
    assertThat(check.get("findingsTotal").asInt()).isEqualTo(120);
    assertThat(check.get("findingsTruncated").asBoolean()).isTrue();
    final JsonTree first = check.get("findings").get(0);
    assertThat(first.get("tool").asString()).isEqualTo("checkstyle");
    assertThat(first.get("rule").asString()).isEqualTo("MissingJavadocMethod");
    assertThat(first.get("severity").asString()).isEqualTo("error");
    assertThat(first.get("message").asString()).isEqualTo("Missing a Javadoc comment.");
    assertThat(first.get("file").asString()).isEqualTo("src/main/java/Foo.java");
    assertThat(first.get("line").asInt()).isEqualTo(1);
    assertThat(first.get("column").asInt()).isEqualTo(5);
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
        IntStream.rangeClosed(1, 500).mapToObj(i -> "line " + i).collect(Collectors.joining("\n"));

    final JsonTree check = render(result("tests", GateStatus.FAILED, log)).get("checks").get(0);

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
