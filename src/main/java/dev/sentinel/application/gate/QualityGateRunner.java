package dev.sentinel.application.gate;

import dev.sentinel.domain.gate.CheckReport;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.QualityGate;
import dev.sentinel.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/**
 * Executes gates in order and aggregates their results. A failing gate does not stop the others.
 */
public class QualityGateRunner {

  @Inject
  public QualityGateRunner() {}

  public CheckReport run(Project project, List<QualityGate> gates) {
    List<GateResult> results = new ArrayList<>();
    for (QualityGate gate : gates) {
      results.add(gate.execute(project));
    }
    return new CheckReport(project, results);
  }
}
