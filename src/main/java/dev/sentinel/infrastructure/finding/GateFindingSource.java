package dev.sentinel.infrastructure.finding;

import dev.sentinel.domain.gate.Finding;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.project.Project;
import java.time.Instant;
import java.util.List;

/** Extracts findings for one gate id from the artifacts its tool writes. */
interface GateFindingSource {
  String gateId();

  List<Finding> extract(Project project, GateResult result, Instant gateStart);
}
