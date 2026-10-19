package dev.sentinel.domain.gate;

import dev.sentinel.domain.project.Project;
import java.time.Duration;
import java.util.List;

public final class SkippedQualityGate implements QualityGate {
  private final String name;
  private final String summary;

  public SkippedQualityGate(final String name) {
    this(name, "Disabled by configuration");
  }

  public SkippedQualityGate(final String name, final String summary) {
    this.name = name;
    this.summary = summary;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public GateResult execute(Project project) {
    return new GateResult(name, GateStatus.SKIPPED, List.of(), 0, Duration.ZERO, "", "", summary);
  }
}
