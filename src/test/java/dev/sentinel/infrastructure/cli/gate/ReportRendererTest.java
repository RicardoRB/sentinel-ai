package dev.sentinel.infrastructure.cli.gate;


import java.util.stream.IntStream;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import org.junit.jupiter.api.Test;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportRendererTest {

    private final Project project =
            new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.SPRING_BOOT);

    private CheckReport report(GateStatus status, String stdout, String stderr) {
        return new CheckReport(project, List.of(new GateResult("tests", status, List.of("./mvnw", "test"),
                status == GateStatus.PASSED ? 0 : 1, Duration.ofMillis(3847), stdout, stderr)));
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
        JsonNode root = JsonMapper.builder().build().readTree(new JsonReportRenderer().renderError("nope"));

        assertThat(root.get("status").asString()).isEqualTo("ERROR");
        assertThat(root.get("error").asString()).isEqualTo("nope");
    }

    @Test
    void textReportForPassingGate() {
        String text = new TextReportRenderer().render(report(GateStatus.PASSED, "ok", ""));

        assertThat(text).isEqualTo(String.join(System.lineSeparator(),
                "Sentinel", "", "✓ tests        PASSED    3.85s", "", "Quality gate: PASSED", ""));
    }

    @Test
    void textReportForFailingGateShowsCommandAndOutputTail() {
        String stdout = String.join("\n", IntStream.rangeClosed(1, 100)
                .mapToObj(i -> "line " + i).toList());

        String text = new TextReportRenderer().render(report(GateStatus.FAILED, stdout, "boom"));

        assertThat(text).contains("✗ tests        FAILED    3.85s", "Quality gate: FAILED",
                "Command:" + System.lineSeparator() + "./mvnw test", "line 100", "boom")
                .doesNotContain("line 60" + System.lineSeparator());
    }
}
