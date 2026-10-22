package dev.ward.infrastructure.finding;

import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.project.Project;
import java.time.Instant;
import java.util.List;

/** Extracts findings for one gate id from the artifacts its tool writes. */
interface GateFindingSource {
  String gateId();

  List<Finding> extract(Project project, GateResult result, Instant gateStart);
}
