package dev.sentinel.application.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.domain.FakeCommandExecutor;
import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.gate.QualityGate;
import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.process.CommandResult;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExtendedGatesTest {
  private static final Project PROJECT =
      new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);

  private static SentinelConfiguration config(String id, List<String> command) {
    final Map<String, GateConfiguration> gates = new LinkedHashMap<>();
    gates.put(id, new GateConfiguration(true, command));
    return new SentinelConfiguration(1, gates);
  }

  @Test
  void acceptsEveryNewAndLegacyIdentifierInConfigurationOrder() {
    final Map<String, GateConfiguration> gates = new LinkedHashMap<>();
    for (final String id :
        List.of(
            "format",
            "semgrep",
            "gitleaks",
            "trivy",
            "enforcer",
            "license",
            "api-compat",
            "compliance",
            "architecture")) {
      gates.put(id, new GateConfiguration(true, List.of("tool")));
    }
    final List<QualityGate> created =
        new QualityGateFactory(new FakeCommandExecutor(0, "", ""))
            .create(new SentinelConfiguration(1, gates));

    assertThat(created).extracting(QualityGate::name).containsExactlyElementsOf(gates.keySet());
  }

  @Test
  void unknownGateErrorListsNewIdentifiers() {
    final QualityGateFactory factory = new QualityGateFactory(new FakeCommandExecutor(0, "", ""));

    assertThatThrownBy(() -> factory.create(config("nope", List.of("x"))))
        .isInstanceOf(SentinelException.class)
        .hasMessageContaining("gitleaks", "zap", "api-compat");
  }

  @Test
  void zapRequiresExplicitTarget() {
    final QualityGateFactory factory = new QualityGateFactory(new FakeCommandExecutor(0, "", ""));

    assertThatThrownBy(() -> factory.create(config("zap", List.of("zap-baseline.py"))))
        .isInstanceOf(SentinelException.class)
        .hasMessageContaining("target");
    assertThatThrownBy(() -> factory.create(config("zap", List.of("zap-baseline.py", "-t"))))
        .isInstanceOf(SentinelException.class);
    assertThatThrownBy(
            () ->
                factory.create(
                    config(
                        "zap", List.of("zap-baseline.py", "-t", ZapTargetValidation.PLACEHOLDER))))
        .isInstanceOf(SentinelException.class);
    assertThatCode(
            () ->
                factory.create(
                    config("zap", List.of("zap-baseline.py", "-t", "http://localhost:8080"))))
        .doesNotThrowAnyException();
  }

  @Test
  void disabledZapWithoutTargetIsSkippedNotRejected() {
    final Map<String, GateConfiguration> gates =
        Map.of("zap", new GateConfiguration(false, List.of("z")));

    assertThat(
            new QualityGateFactory(new FakeCommandExecutor(0, "", ""))
                .create(new SentinelConfiguration(1, gates)))
        .hasSize(1);
  }

  @Test
  void missingBinaryReportsInstallHint() {
    final CommandExecutor missing =
        (command, dir) ->
            new CommandResult(-1, "", "", Duration.ZERO, "Could not start [gitleaks]: not found");
    final GateResult result =
        new CommandQualityGate("gitleaks", missing, List.of("gitleaks", "detect")).execute(PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.UNAVAILABLE);
    assertThat(result.summary()).contains("gitleaks").contains("Install gitleaks");
  }

  @Test
  void failedSecretScanDoesNotAddMatchedValuesToSummary() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(1, "", "leaks found: 1");
    final GateResult result =
        new CommandQualityGate("gitleaks", executor, List.of("gitleaks", "detect", "--redact"))
            .execute(PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.exitCode()).isEqualTo(1);
    assertThat(String.valueOf(result.summary())).doesNotContain("AKIA");
  }

  @Test
  void javaDefaultsIncludeNewGates() {
    assertThat(new LanguageGateRegistry().defaults(Language.JAVA))
        .contains(
            "format", "semgrep", "gitleaks", "zap", "trivy", "enforcer", "license", "api-compat");
    assertThat(new LanguageGateRegistry().defaults(Language.GO)).contains("gitleaks", "trivy");
  }
}
