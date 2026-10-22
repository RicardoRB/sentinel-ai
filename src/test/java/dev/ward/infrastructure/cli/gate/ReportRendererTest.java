package dev.ward.infrastructure.cli.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ReportRendererTest {
  private static final long DURATION_MILLIS = 3847;
  private static final int LOG_LINES = 100;
  private static final int ERRORS = 3;
  private static final int WARNINGS = 7;

  private final Project project =
      new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.SPRING_BOOT);

  private CheckReport report(final GateStatus status, final String stdout, final String stderr) {
    return new CheckReport(
        project,
        List.of(
            GateResult.builder("tests", status)
                .command(List.of("./mvnw", "test"))
                .exitCode(status == GateStatus.PASSED ? 0 : 1)
                .duration(Duration.ofMillis(DURATION_MILLIS))
                .stdout(stdout)
                .stderr(stderr)
                .build()));
  }

  @Test
  void jsonReportHasDocumentedShape() {
    final String json =
        new JsonReportRenderer(new ForyJsonCodec())
            .render(report(GateStatus.FAILED, "line \"1\"\n", "err"));

    final JsonTree root = JsonTree.parse(json);
    assertThat(root.get("status").asString()).isEqualTo("FAILED");
    assertThat(root.get("project").get("language").asString()).isEqualTo("JAVA");
    assertThat(root.get("project").get("buildTool").asString()).isEqualTo("MAVEN");
    assertThat(root.get("project").get("framework").asString()).isEqualTo("SPRING_BOOT");
    final JsonTree check = root.get("checks").get(0);
    assertThat(check.get("name").asString()).isEqualTo("tests");
    assertThat(check.get("status").asString()).isEqualTo("FAILED");
    assertThat(check.get("command").asString()).isEqualTo("./mvnw test");
    assertThat(check.get("exitCode").asInt()).isEqualTo(1);
    assertThat(check.get("durationMs").asLong()).isEqualTo(DURATION_MILLIS);
    assertThat(check.get("output").asString()).isEqualTo("line \"1\"\n");
    assertThat(check.has("stdout")).isFalse();
    assertThat(check.has("stderr")).isFalse();
  }

  @Test
  void jsonErrorDocument() {
    final JsonTree root =
        JsonTree.parse(new JsonReportRenderer(new ForyJsonCodec()).renderError("nope"));

    assertThat(root.get("status").asString()).isEqualTo("ERROR");
    assertThat(root.get("error").asString()).isEqualTo("nope");
  }

  @Test
  void textReportForPassingGate() {
    final String text = new TextReportRenderer().render(report(GateStatus.PASSED, "ok", ""));

    assertThat(text)
        .contains("Ward dev", "✓ tests", "Quality Gate: PASSED", "1 passed · 0 failed · 0 skipped");
  }

  @Test
  void textReportForFailingGateShowsCommandAndOutputTail() {
    final String stdout =
        String.join("\n", IntStream.rangeClosed(1, LOG_LINES).mapToObj(i -> "line " + i).toList());

    final String text = new TextReportRenderer().render(report(GateStatus.FAILED, stdout, "boom"));

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
    final CheckReport report =
        new CheckReport(
            project,
            List.of(
                GateResult.builder("customGate", GateStatus.SKIPPED)
                    .command(List.of())
                    .exitCode(0)
                    .duration(Duration.ZERO)
                    .stdout("")
                    .stderr("")
                    .build(),
                GateResult.builder("gitleaks", GateStatus.UNAVAILABLE)
                    .command(List.of("gitleaks"))
                    .exitCode(-1)
                    .duration(Duration.ZERO)
                    .stdout("")
                    .stderr("missing")
                    .build()));

    final String text = new TextReportRenderer().render(report);

    assertThat(text)
        .contains("– customGate", "✗ Gitleaks — unavailable", "0 passed · 1 failed · 1 skipped")
        .doesNotContain("\u001b", "3 errors", "0 errors");
  }

  @Test
  void skippedFailFastLineExplainsWhyGateDidNotRun() {
    final CheckReport report =
        new CheckReport(
            project,
            List.of(
                GateResult.builder("compile", GateStatus.SKIPPED)
                    .command(List.of())
                    .exitCode(0)
                    .duration(Duration.ZERO)
                    .stdout("")
                    .stderr("")
                    .summary("not run (fail-fast)")
                    .build()));

    assertThat(new TextReportRenderer().render(report))
        .contains("– compile — not run (fail-fast)", "0 passed · 0 failed · 1 skipped");
  }

  @Test
  void textReportShowsReportedFindingCountsOnly() {
    final GateResult counted =
        GateResult.builder("checkstyle", GateStatus.FAILED)
            .command(List.of("checkstyle"))
            .exitCode(1)
            .duration(Duration.ZERO)
            .stdout("")
            .stderr("")
            .summary(null)
            .errors(ERRORS)
            .warnings(WARNINGS)
            .build();
    final CheckReport report = new CheckReport(project, List.of(counted));

    assertThat(new TextReportRenderer().render(report))
        .contains("✗ Checkstyle — 3 errors", "3 errors · 7 warnings");
  }
}
