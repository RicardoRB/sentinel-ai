package dev.ward.application.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.process.CommandExecutor;
import dev.ward.domain.process.CommandResult;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OpenApiLintGateTest {
  private static final String LINT = "openapi-lint";

  @Test
  void runsVacuumWithTheSpecAppended() {
    final OpenApiGateFixtures fixtures = OpenApiGateFixtures.committedSpec();
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

    final GateResult result =
        fixtures
            .gate(LINT, OpenApiGateFixtures.LINT_COMMAND, executor)
            .execute(OpenApiGateFixtures.PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.PASSED);
    assertThat(executor.commands)
        .containsExactly(List.of("vacuum", "lint", "-z", "-n", "warn", OpenApiGateFixtures.SPEC));
    assertThat(executor.workingDirectories).containsExactly(OpenApiGateFixtures.PROJECT.root());
  }

  @Test
  void lintFindingsFailTheGate() {
    final GateResult result =
        OpenApiGateFixtures.committedSpec()
            .gate(LINT, OpenApiGateFixtures.LINT_COMMAND, new FakeCommandExecutor(1, "", ""))
            .execute(OpenApiGateFixtures.PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
  }

  @Test
  void missingSpecFailsWithoutRunningVacuum() {
    final OpenApiGateFixtures fixtures = new OpenApiGateFixtures();
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

    final GateResult result =
        fixtures
            .gate(LINT, OpenApiGateFixtures.LINT_COMMAND, executor)
            .execute(OpenApiGateFixtures.PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).contains(OpenApiGateFixtures.SPEC).contains("does not exist");
    assertThat(executor.commands).isEmpty();
  }

  @Test
  void vacuumConfigurationFileFailsTheGate() {
    final OpenApiGateFixtures fixtures = OpenApiGateFixtures.committedSpec();
    fixtures.workingTree.put("vacuum.conf.yaml", "lint:\n  fail-severity: none\n");
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

    final GateResult result =
        fixtures
            .gate(LINT, OpenApiGateFixtures.LINT_COMMAND, executor)
            .execute(OpenApiGateFixtures.PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).contains("vacuum.conf.yaml").doesNotContain("fail-severity");
    assertThat(executor.commands).isEmpty();
  }

  @Test
  void inlineLintIgnoreFailsTheGate() {
    final OpenApiGateFixtures fixtures = OpenApiGateFixtures.committedSpec();
    fixtures.workingTree.put(
        OpenApiGateFixtures.SPEC, "paths:\n  /a:\n    get:\n      x-lint-ignore: operation-tags\n");
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

    final GateResult result =
        fixtures
            .gate(LINT, OpenApiGateFixtures.LINT_COMMAND, executor)
            .execute(OpenApiGateFixtures.PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).contains("x-lint-ignore").contains("not allowed");
    assertThat(executor.commands).isEmpty();
  }

  @Test
  void missingVacuumIsUnavailableWithInstallGuidance() {
    final CommandExecutor missing =
        (command, dir) ->
            new CommandResult(-1, "", "", Duration.ZERO, "Could not start [vacuum]: not found");

    final GateResult result =
        OpenApiGateFixtures.committedSpec()
            .gate(LINT, OpenApiGateFixtures.LINT_COMMAND, missing)
            .execute(OpenApiGateFixtures.PROJECT);

    assertThat(result.status()).isEqualTo(GateStatus.UNAVAILABLE);
    assertThat(result.summary()).contains("Install vacuum");
  }

  @Test
  void missingSpecOptionIsAConfigurationError() {
    final OpenApiGates gates =
        OpenApiGateFixtures.committedSpec().gates(new FakeCommandExecutor(0, "", ""));

    assertThatThrownBy(
            () ->
                gates.create(
                    LINT,
                    new GateConfiguration(true, OpenApiGateFixtures.LINT_COMMAND, Set.of("ci")),
                    OpenApiGateFixtures.PROJECT))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("quality-gates.openapi-lint.spec");
  }
}
