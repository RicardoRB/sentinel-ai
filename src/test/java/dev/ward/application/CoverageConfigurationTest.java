package dev.ward.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.agent.AgentResult;
import dev.ward.domain.config.CommandLineTokenizer;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.init.InitResult;
import dev.ward.domain.loop.LoopConfiguration;
import dev.ward.domain.policy.PolicyEvaluator;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.cli.gate.JsonReportRenderer;
import dev.ward.infrastructure.cli.gate.TextReportRenderer;
import dev.ward.infrastructure.config.TomlConfigurationReader;
import dev.ward.infrastructure.json.ForyJsonCodec;
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
  private static final int TIMEOUT_SECONDS = 3;
  private static final Duration TESTS_DURATION = Duration.ofMillis(1500);
  private static final Project PROJECT =
      new Project(Path.of("/project"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);

  @Test
  void configurationAndTokenizerCoverProfilesAndErrors(@TempDir final Path root)
      throws IOException {
    assertThat(CommandLineTokenizer.tokenize("java -Dname='hello world' app"))
        .containsExactly("java", "-Dname=hello world", "app");
    assertThatThrownBy(() -> CommandLineTokenizer.tokenize("'unterminated"))
        .isInstanceOf(WardException.class);
    final Path file = root.resolve("ward.toml");
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
    final WardConfiguration configuration = new TomlConfigurationReader().read(file);
    assertThat(configuration.gates()).containsOnlyKeys("tests", "compile");
    assertThat(configuration.profileNames()).containsExactly("fast");
    assertThat(configuration.enabledGates()).containsOnlyKeys("compile");
    assertThatThrownBy(() -> new TomlConfigurationReader().read(root.resolve("missing.toml")))
        .isInstanceOf(WardException.class);
  }

  @Test
  void reportAndPolicyModelsCoverStrictAndNonStrictResults() {
    final GateResult passed =
        GateResult.builder("tests", GateStatus.PASSED)
            .command(List.of("test"))
            .exitCode(0)
            .duration(Duration.ZERO)
            .stdout("ok")
            .stderr("")
            .build();
    final GateResult failed =
        GateResult.builder("checkstyle", GateStatus.FAILED)
            .command(List.of("check"))
            .exitCode(1)
            .duration(Duration.ZERO)
            .stdout("")
            .stderr("bad")
            .build();
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
    assertThat(new LoopConfiguration(2, TIMEOUT_SECONDS, true).allowDirty()).isTrue();
    assertThat(new GateConfiguration(true, List.of("test")).command()).containsExactly("test");
    assertThat(new WardConfiguration(1, Map.of()).enabledGates()).isEmpty();
    assertThat(new AgentResult(true, "done", "").succeeded()).isTrue();
    assertThat(
            new InitResult(
                    Path.of("/tmp/ward.toml"),
                    true,
                    false,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of())
                .gates())
        .isEmpty();
  }

  @Test
  void rendersTextAndJsonReportsIncludingFailureOutput() {
    final GateResult passed =
        GateResult.builder("tests", GateStatus.PASSED)
            .command(List.of("test"))
            .exitCode(0)
            .duration(TESTS_DURATION)
            .stdout("ok")
            .stderr("")
            .build();
    final GateResult failed =
        GateResult.builder("lint", GateStatus.FAILED)
            .command(List.of("lint"))
            .exitCode(1)
            .duration(Duration.ofMillis(1))
            .stdout("line 1\nline 2")
            .stderr("stderr")
            .build();
    final CheckReport report = new CheckReport(PROJECT, List.of(passed, failed));
    assertThat(new TextReportRenderer().render(report))
        .contains("Ward", "Quality Gate: FAILED", "Command:", "line 1");
    assertThat(new JsonReportRenderer(new ForyJsonCodec()).render(report))
        .contains("schemaVersion", "FAILED", "lint", "output", "line 1")
        .doesNotContain("\"stderr\"");
    assertThat(new JsonReportRenderer(new ForyJsonCodec()).renderError("broken"))
        .contains("ERROR", "broken");
  }
}
