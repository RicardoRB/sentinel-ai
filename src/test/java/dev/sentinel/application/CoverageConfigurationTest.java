package dev.sentinel.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.domain.agent.AgentResult;
import dev.sentinel.domain.config.CommandLineTokenizer;
import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.init.InitResult;
import dev.sentinel.domain.loop.LoopConfiguration;
import dev.sentinel.domain.policy.PolicyEvaluator;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import dev.sentinel.infrastructure.cli.gate.JsonReportRenderer;
import dev.sentinel.infrastructure.cli.gate.TextReportRenderer;
import dev.sentinel.infrastructure.config.TomlConfigurationReader;
import dev.sentinel.infrastructure.json.ForyJsonCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// This test intentionally aggregates coverage across the application services.
// Splitting it would duplicate setup and obscure the cross-service scenarios.
class CoverageConfigurationTest {
  private static final Project PROJECT =
      new Project(Path.of("/project"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);

  @Test
  void configurationAndTokenizerCoverProfilesAndErrors(@TempDir final Path root)
      throws IOException {
    assertThat(CommandLineTokenizer.tokenize("java -Dname='hello world' app"))
        .containsExactly("java", "-Dname=hello world", "app");
    assertThatThrownBy(() -> CommandLineTokenizer.tokenize("'unterminated"))
        .isInstanceOf(SentinelException.class);
    final Path file = root.resolve("sentinel.toml");
    Files.writeString(
        file,
        """
                version = 1
                [quality-gates.tests]
                enabled = false
                profiles = ["fast"]
                [quality-gates.compile]
                command = ["mvn", "compile"]
                profiles = ["fast"]
                """);
    final SentinelConfiguration configuration = new TomlConfigurationReader().read(file);
    assertThat(configuration.gates()).containsOnlyKeys("tests", "compile");
    assertThat(configuration.profileNames()).containsExactly("fast");
    assertThat(configuration.enabledGates()).containsOnlyKeys("compile");
    assertThatThrownBy(() -> new TomlConfigurationReader().read(root.resolve("missing.toml")))
        .isInstanceOf(SentinelException.class);
  }

  @Test
  void reportAndPolicyModelsCoverStrictAndNonStrictResults() {
    final GateResult passed =
        new GateResult("tests", GateStatus.PASSED, List.of("test"), 0, Duration.ZERO, "ok", "");
    final GateResult failed =
        new GateResult(
            "checkstyle", GateStatus.FAILED, List.of("check"), 1, Duration.ZERO, "", "bad");
    final CheckReport report = new CheckReport(PROJECT, List.of(passed, failed));
    assertThat(report.passed()).isFalse();
    assertThat(report.status()).isEqualTo(GateStatus.FAILED);
    assertThat(new PolicyEvaluator().evaluate(report, false)).isNotEmpty();
    assertThat(new PolicyEvaluator().evaluate(report, true)).isNotEmpty();
    assertThat(new CheckReport(PROJECT, List.of(passed)).passed()).isTrue();
  }

  @Test
  void validatesConfigurationAndValueObjects() {
    assertThatThrownBy(() -> new LoopConfiguration(0, 1, false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LoopConfiguration(1, 0, false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(new LoopConfiguration(2, 3, true).allowDirty()).isTrue();
    assertThat(new GateConfiguration(true, List.of("test")).command()).containsExactly("test");
    assertThat(new SentinelConfiguration(1, Map.of()).enabledGates()).isEmpty();
    assertThat(new AgentResult(true, "done", "").succeeded()).isTrue();
    assertThat(
            new InitResult(Path.of("/tmp/sentinel.toml"), true, false, null, null, null, null)
                .gates())
        .isEmpty();
  }

  @Test
  void rendersTextAndJsonReportsIncludingFailureOutput() {
    final GateResult passed =
        new GateResult(
            "tests", GateStatus.PASSED, List.of("test"), 0, Duration.ofMillis(1500), "ok", "");
    final GateResult failed =
        new GateResult(
            "lint",
            GateStatus.FAILED,
            List.of("lint"),
            1,
            Duration.ofMillis(1),
            "line 1\nline 2",
            "stderr");
    final CheckReport report = new CheckReport(PROJECT, List.of(passed, failed));
    assertThat(new TextReportRenderer().render(report))
        .contains("Sentinel", "Quality Gate: FAILED", "Command:", "line 1");
    assertThat(new JsonReportRenderer(new ForyJsonCodec()).render(report))
        .contains("schemaVersion", "FAILED", "lint", "stderr");
    assertThat(new JsonReportRenderer(new ForyJsonCodec()).renderError("broken"))
        .contains("ERROR", "broken");
  }
}
