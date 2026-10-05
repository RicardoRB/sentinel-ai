package dev.sentinel.domain.gate;

import dev.sentinel.domain.project.Project;

public interface QualityGate {

  String name();

  GateResult execute(Project project);
}
