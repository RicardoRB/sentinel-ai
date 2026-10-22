package dev.ward.domain.gate;

import dev.ward.domain.project.Project;
import java.time.Duration;
import java.util.List;

/** A gate that does not run and reports SKIPPED with a reason. */
public final class SkippedQualityGate implements QualityGate {
  private final String name;
  private final String summary;

  /** Creates a gate skipped because it is disabled in the configuration. */
  public SkippedQualityGate(final String name) {
    this(name, "Disabled by configuration");
  }

  /** Creates a gate skipped for {@code summary}. */
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
    return GateResult.builder(name, GateStatus.SKIPPED)
        .command(List.of())
        .exitCode(0)
        .duration(Duration.ZERO)
        .stdout("")
        .stderr("")
        .summary(summary)
        .build();
  }
}
