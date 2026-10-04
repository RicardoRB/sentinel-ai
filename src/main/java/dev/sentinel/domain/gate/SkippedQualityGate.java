package dev.sentinel.domain.gate;

import dev.sentinel.domain.project.Project;

import java.time.Duration;
import java.util.List;

public final class SkippedQualityGate implements QualityGate {
    private final String name;
    public SkippedQualityGate(String name) { this.name = name; }
    @Override public String name() { return name; }
    @Override public GateResult execute(Project project) {
        return new GateResult(name, GateStatus.SKIPPED, List.of(), 0, Duration.ZERO, "", "", "Disabled by configuration");
    }
}
