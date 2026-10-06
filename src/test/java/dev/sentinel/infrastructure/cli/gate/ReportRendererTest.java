package dev.sentinel.infrastructure.cli.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ReportRendererTest {

  private final Project project =
      new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.SPRING_BOOT);

  private CheckReport report(GateStatus status, String stdout, String stderr) {
    return new CheckReport(
        project,
        List.of(
            new GateResult(
                "tests",
                status,
                List.of("./mvnw", "test"),
                status == GateStatus.PASSED ? 0 : 1,
                Duration.ofMillis(3847),
                stdout,
                stderr)));
  }

  @Test
  void jsonReportHasDocumentedShape() {
    String json = new JsonReportRenderer().render(report(GateStatus.FAILED, "line \"1\"\n", "err"));

    JsonNode root = JsonMapper.builder().build().readTree(json);
    assertThat(root.get("status").asString()).isEqualTo("FAILED");
    assertThat(root.get("project").get("language").asString()).isEqualTo("JAVA");
    assertThat(root.get("project").get("buildTool").asString()).isEqualTo("MAVEN");
    assertThat(root.get("project").get("framework").asString()).isEqualTo("SPRING_BOOT");
    JsonNode check = root.get("checks").get(0);
    assertThat(check.get("name").asString()).isEqualTo("tests");
    assertThat(check.get("status").asString()).isEqualTo("FAILED");
    assertThat(check.get("command").asString()).isEqualTo("./mvnw test");
    assertThat(check.get("exitCode").asInt()).isEqualTo(1);
    assertThat(check.get("durationMs").asLong()).isEqualTo(3847);
    assertThat(check.get("stdout").asString()).isEqualTo("line \"1\"\n");
    assertThat(check.get("stderr").asString()).isEqualTo("err");
  }

  @Test
  void jsonErrorDocument() {
    JsonNode root =
        JsonMapper.builder().build().readTree(new JsonReportRenderer().renderError("nope"));

    assertThat(root.get("status").asString()).isEqualTo("ERROR");
    assertThat(root.get("error").asString()).isEqualTo("nope");
  }

  @Test
  void textReportForPassingGate() {
    String text = new TextReportRenderer().render(report(GateStatus.PASSED, "ok", ""));

    assertThat(text)
        .contains(
            "Sentinel dev", "✓ tests", "Quality Gate: PASSED", "1 passed · 0 failed · 0 skipped");
  }

  @Test
  void textReportForFailingGateShowsCommandAndOutputTail() {
    String stdout =
        String.join("\n", IntStream.rangeClosed(1, 100).mapToObj(i -> "line " + i).toList());

    String text = new TextReportRenderer().render(report(GateStatus.FAILED, stdout, "boom"));

    assertThat(text)
        .contains(
            "✗ tests",
            "Quality Gate: FAILED",
            "0 passed · 1 failed · 0 skipped",
            "Command:" + System.lineSeparator() + "./mvnw test",
            "line 100",
            "boom")
        .doesNotContain("line 60" + System.lineSeparator());
  }

  @Test
  void textReportExplainsSkippedAndUnavailableAndDoesNotEmitAnsi() {
    CheckReport report =
        new CheckReport(
            project,
            List.of(
                new GateResult(
                    "customGate", GateStatus.SKIPPED, List.of(), 0, Duration.ZERO, "", ""),
                new GateResult(
                    "gitleaks",
                    GateStatus.UNAVAILABLE,
                    List.of("gitleaks"),
                    -1,
                    Duration.ZERO,
                    "",
                    "missing")));

    String text = new TextReportRenderer().render(report);

    assertThat(text)
        .contains("– customGate", "✗ Gitleaks — unavailable", "0 passed · 1 failed · 1 skipped")
        .doesNotContain("\u001b", "3 errors", "0 errors");
  }

  @Test
  void textReportShowsReportedFindingCountsOnly() {
    GateResult counted =
        new GateResult(
            "checkstyle",
            GateStatus.FAILED,
            List.of("checkstyle"),
            1,
            Duration.ZERO,
            "",
            "",
            null,
            3,
            7);
    CheckReport report = new CheckReport(project, List.of(counted));

    assertThat(new TextReportRenderer().render(report))
        .contains("✗ Checkstyle — 3 errors", "3 errors · 7 warnings");
  }
}
