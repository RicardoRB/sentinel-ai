package dev.sentinel.application.gate;

import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.GateStatus;
import dev.sentinel.domain.gate.QualityGate;
import dev.sentinel.domain.project.Project;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Executes gates in order and aggregates their results. A failing gate does not stop the others.
 */
public class QualityGateRunner {
  private static final Logger LOGGER = Logger.getLogger(QualityGateRunner.class.getName());

  @Inject
  public QualityGateRunner() {}

  public CheckReport run(final Project project, final List<QualityGate> gates) {
    return run(project, gates, CheckProgressListener.NO_OP, false);
  }

  public CheckReport run(
      final Project project, final List<QualityGate> gates, final CheckProgressListener listener) {
    return run(project, gates, listener, false);
  }

  public CheckReport run(
      final Project project, final List<QualityGate> gates, final boolean failFast) {
    return run(project, gates, CheckProgressListener.NO_OP, failFast);
  }

  public CheckReport run(
      final Project project,
      final List<QualityGate> gates,
      final CheckProgressListener listener,
      final boolean failFast) {
    final List<GateResult> results = new ArrayList<>();
    for (int i = 0; i < gates.size(); i++) {
      final QualityGate gate = gates.get(i);
      listener.gateStarted(gate.name());
      final long start = System.nanoTime();
      LOGGER.log(Level.INFO, () -> "event=gate-start gate=" + gate.name());
      final GateResult result = gate.execute(project);
      results.add(result);
      listener.gateFinished(result);
      LOGGER.log(
          Level.INFO,
          () ->
              "event=gate-complete gate="
                  + gate.name()
                  + " status="
                  + result.status()
                  + " duration-ms="
                  + ((System.nanoTime() - start) / 1_000_000));
      if (failFast && result.status() != GateStatus.PASSED) {
        for (final QualityGate skipped : gates.subList(i + 1, gates.size())) {
          final GateResult skippedResult = skippedByFailFast(skipped);
          results.add(skippedResult);
          listener.gateFinished(skippedResult);
          LOGGER.log(
              Level.INFO, () -> "event=gate-skip gate=" + skipped.name() + " reason=fail-fast");
        }
        break;
      }
    }
    return new CheckReport(project, results);
  }

  private static GateResult skippedByFailFast(final QualityGate gate) {
    return new GateResult(
        gate.name(),
        GateStatus.SKIPPED,
        List.of(),
        0,
        Duration.ZERO,
        "",
        "",
        "not run (fail-fast)");
  }
}
