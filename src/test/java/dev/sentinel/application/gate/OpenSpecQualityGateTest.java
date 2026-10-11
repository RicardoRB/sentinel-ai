package dev.sentinel.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.FakeCommandExecutor;
import dev.sentinel.domain.doctor.EnvironmentFacts;
import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.doctor.ExecutableObservation;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OpenSpecQualityGateTest {
  private static final Project PROJECT =
      new Project(Path.of("/project"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);

  @Test
  void missingExecutableIsUnavailableWithoutExecution() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");
    final OpenSpecQualityGate gate =
        new OpenSpecQualityGate("openspec", executor, inspection(false, true), command());

    assertThat(gate.execute(PROJECT).status()).isEqualTo(GateStatus.UNAVAILABLE);
    assertThat(executor.commands).isEmpty();
  }

  @Test
  void missingDirectoryFailsWithoutExecution() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");
    final OpenSpecQualityGate gate =
        new OpenSpecQualityGate("openspec", executor, inspection(true, false), command());

    final GateResult result = gate.execute(PROJECT);
    assertThat(result.status()).isEqualTo(GateStatus.FAILED);
    assertThat(result.summary()).contains("openspec/");
    assertThat(executor.commands).isEmpty();
  }

  @Test
  void availablePrerequisitesRunConfiguredVectorFromProjectRoot() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "ok", "");
    final OpenSpecQualityGate gate =
        new OpenSpecQualityGate("openspec-archived", executor, inspection(true, true), command());

    assertThat(gate.execute(PROJECT).status()).isEqualTo(GateStatus.PASSED);
    assertThat(executor.commands).containsExactly(command());
    assertThat(executor.workingDirectories).containsExactly(PROJECT.root());
  }

  private static List<String> command() {
    return List.of("openspec", "validate", "--archived", "--no-interactive");
  }

  private static EnvironmentInspection inspection(
      final boolean executable, final boolean directory) {
    return new EnvironmentInspection() {
      @Override
      public EnvironmentFacts inspect(final Path root) {
        return new EnvironmentFacts(Set.of(), Map.of());
      }

      @Override
      public ExecutableObservation inspectExecutable(final Path root, final String requested) {
        return new ExecutableObservation(
            requested, Path.of(requested), executable, executable, executable);
      }

      @Override
      public boolean hasOpenSpecDirectory(final Path root) {
        return directory;
      }
    };
  }
}
