package dev.sentinel.application.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.FakeCommandExecutor;
import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.gate.QualityGate;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualityGateRunnerTest {

  private final Project project =
      new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  private final QualityGateRunner runner = new QualityGateRunner();

  private static QualityGate gate(String name, GateStatus status) {
    return new QualityGate() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public GateResult execute(Project project) {
        return new GateResult(
            name,
            status,
            List.of("x"),
            status == GateStatus.PASSED ? 0 : 1,
            Duration.ofMillis(10),
            "",
            "");
      }
    };
  }

  @Test
  void allPassedAggregatesToPassed() {
    CheckReport report =
        runner.run(project, List.of(gate("a", GateStatus.PASSED), gate("b", GateStatus.PASSED)));

    assertThat(report.status()).isEqualTo(GateStatus.PASSED);
    assertThat(report.passed()).isTrue();
  }

  @Test
  void oneFailureAggregatesToFailedAndOtherGatesStillRun() {
    CheckReport report =
        runner.run(project, List.of(gate("a", GateStatus.FAILED), gate("b", GateStatus.PASSED)));

    assertThat(report.status()).isEqualTo(GateStatus.FAILED);
    assertThat(report.results()).extracting(GateResult::name).containsExactly("a", "b");
  }

  @Test
  void reportsStartAndFinishInGateOrderDespiteFailure() {
    List<String> events = new ArrayList<>();
    FakeCommandExecutor executor = new FakeCommandExecutor(1, "", "failure");
    runner.run(
        project,
        List.of(
            new CommandQualityGate("a", executor, List.of("first")),
            new CommandQualityGate("b", executor, List.of("second"))),
        new CheckProgressListener() {
          @Override
          public void gateStarted(String name) {
            events.add("start:" + name);
          }

          @Override
          public void gateFinished(GateResult result) {
            events.add("finish:" + result.name());
          }
        });
    assertThat(events).containsExactly("start:a", "finish:a", "start:b", "finish:b");
    assertThat(executor.commands).containsExactly(List.of("first"), List.of("second"));
  }

  @Test
  void failFastStopsAndReportsRemainingGatesAsSkipped() {
    List<String> executed = new ArrayList<>();
    List<String> events = new ArrayList<>();
    QualityGate failure = recordingGate("b", GateStatus.UNAVAILABLE, executed);
    CheckReport report =
        runner.run(
            project,
            List.of(
                recordingGate("a", GateStatus.PASSED, executed),
                failure,
                recordingGate("c", GateStatus.PASSED, executed)),
            new CheckProgressListener() {
              @Override
              public void gateFinished(GateResult result) {
                events.add(result.name() + ":" + result.status());
              }
            },
            true);

    assertThat(executed).containsExactly("a", "b");
    assertThat(report.results())
        .extracting(GateResult::status)
        .containsExactly(GateStatus.PASSED, GateStatus.UNAVAILABLE, GateStatus.SKIPPED);
    assertThat(report.results().get(2).summary()).isEqualTo("not run (fail-fast)");
    assertThat(report.results().get(2).command()).isEmpty();
    assertThat(events).containsExactly("a:PASSED", "b:UNAVAILABLE", "c:SKIPPED");
  }

  @Test
  void failFastRunsEveryGateWhenAllPass() {
    List<String> executed = new ArrayList<>();
    runner.run(
        project,
        List.of(
            recordingGate("a", GateStatus.PASSED, executed),
            recordingGate("b", GateStatus.PASSED, executed)),
        true);
    assertThat(executed).containsExactly("a", "b");
  }

  @Test
  void failFastStopsOnExecutionError() {
    List<String> executed = new ArrayList<>();
    CheckReport report =
        runner.run(
            project,
            List.of(
                recordingGate("a", GateStatus.EXECUTION_ERROR, executed),
                recordingGate("b", GateStatus.PASSED, executed)),
            true);

    assertThat(executed).containsExactly("a");
    assertThat(report.results())
        .extracting(GateResult::status)
        .containsExactly(GateStatus.EXECUTION_ERROR, GateStatus.SKIPPED);
  }

  private static QualityGate recordingGate(String name, GateStatus status, List<String> executed) {
    return new QualityGate() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public GateResult execute(Project ignored) {
        executed.add(name);
        return new GateResult(name, status, List.of("x"), 0, Duration.ZERO, "", "");
      }
    };
  }
}
