package dev.ward.application.spec;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.FakeCommandExecutor;
import dev.ward.domain.doctor.EnvironmentFacts;
import dev.ward.domain.doctor.EnvironmentInspection;
import dev.ward.domain.doctor.ExecutableObservation;
import dev.ward.domain.process.CommandResult;
import dev.ward.domain.spec.SpecFinding;
import dev.ward.domain.spec.SpecItem;
import dev.ward.domain.spec.SpecItemReport;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OpenSpecValidatorTest {
  private static final String OPENSPEC = "openspec";
  private static final Path ROOT = Path.of("/project");
  private static final SpecItem FOO = new SpecItem(SpecItem.Kind.CHANGE, "foo");
  private static final SpecItem AUTH = new SpecItem(SpecItem.Kind.SPEC, "identity/user-auth");

  @Test
  void pathExecutableValidatesEachItemWithTheExactVectorFromTheRoot() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "Change 'foo' is valid", "");

    final List<SpecItemReport> reports =
        new OpenSpecValidator(executor, inspection(true, true))
            .validate(ROOT, OPENSPEC, List.of(FOO, AUTH));

    assertThat(executor.commands)
        .containsExactly(
            List.of(
                OPENSPEC, "validate", "foo", "--type", "change", "--strict", "--no-interactive"),
            List.of(
                OPENSPEC,
                "validate",
                "identity/user-auth",
                "--type",
                "spec",
                "--strict",
                "--no-interactive"));
    assertThat(executor.workingDirectories).containsOnly(ROOT);
    assertThat(reports)
        .extracting(SpecItemReport::title)
        .containsExactly("openspec change foo", "openspec spec identity/user-auth");
    assertThat(reports).noneMatch(SpecItemReport::hasFindings);
  }

  @Test
  void projectLocalExecutableIsUsedAsConfigured() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

    new OpenSpecValidator(executor, inspection(true, true))
        .validate(ROOT, "./node_modules/.bin/openspec", List.of(FOO));

    assertThat(executor.commands.getFirst().getFirst()).isEqualTo("./node_modules/.bin/openspec");
  }

  @Test
  void unavailableExecutableLaunchesNothingAndExplains() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

    final List<SpecItemReport> reports =
        new OpenSpecValidator(executor, inspection(false, true))
            .validate(ROOT, OPENSPEC, List.of(FOO));

    assertThat(executor.commands).isEmpty();
    assertThat(reports)
        .singleElement()
        .satisfies(
            report -> {
              assertThat(report.title()).isEqualTo(OPENSPEC);
              assertThat(report.findings().getFirst().message())
                  .contains("unavailable", "Install OpenSpec");
            });
  }

  @Test
  void missingDirectoryLaunchesNothing() {
    final FakeCommandExecutor executor = new FakeCommandExecutor(0, "", "");

    final List<SpecItemReport> reports =
        new OpenSpecValidator(executor, inspection(true, false))
            .validate(ROOT, OPENSPEC, List.of(FOO));

    assertThat(executor.commands).isEmpty();
    assertThat(reports.getFirst().findings().getFirst().message()).contains("openspec/ directory");
  }

  @Test
  void failureFindingsComeFromStderr() {
    final FakeCommandExecutor executor =
        new FakeCommandExecutor(1, "", "✗ [ERROR] x/spec.md: needs a scenario\nNext steps:\n");

    final List<SpecItemReport> reports =
        new OpenSpecValidator(executor, inspection(true, true))
            .validate(ROOT, OPENSPEC, List.of(FOO));

    assertThat(reports.getFirst().findings())
        .containsExactly(new SpecFinding(SpecFinding.Level.ERROR, "x/spec.md", "needs a scenario"));
  }

  @Test
  void startFailureIsReportedAsAFinding() {
    final OpenSpecValidator validator =
        new OpenSpecValidator(
            (command, directory) ->
                new CommandResult(-1, "", "", Duration.ZERO, "PROCESS_START_FAILURE"),
            inspection(true, true));

    final List<SpecItemReport> reports = validator.validate(ROOT, OPENSPEC, List.of(FOO));

    assertThat(reports.getFirst().findings().getFirst().message())
        .contains("could not be run", "PROCESS_START_FAILURE");
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
