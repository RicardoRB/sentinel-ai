package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.CompatibilityFinding;
import dev.sentinel.domain.init.CompatibilityInspection;
import dev.sentinel.domain.init.CompatibilityPlan;
import dev.sentinel.domain.init.CompatibilityResolver;
import dev.sentinel.domain.init.ManagedPluginCatalog;
import dev.sentinel.domain.project.Project;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Resolves compatible catalog versions for the managed Maven plugins a selection needs. */
final class CompatibilityFindings {
  private final CompatibilityInspection inspection;
  private final CompatibilityResolver resolver = new CompatibilityResolver();

  CompatibilityFindings(final CompatibilityInspection inspection) {
    this.inspection = inspection;
  }

  /** Returns one finding per managed artifact, failing when any cannot be resolved safely. */
  Map<String, CompatibilityFinding> resolve(final Project project, final List<String> artifacts) {
    final List<String> managed =
        artifacts.stream().filter(ManagedPluginCatalog.entries()::containsKey).toList();
    final CompatibilityPlan plan =
        resolver.resolve(inspection.inspect(project), ManagedPluginCatalog.entries(), managed);
    if (plan.hasUnresolvableFindings()) {
      throw new SentinelException(
          plan.findings().stream()
              .filter(
                  finding ->
                      "UNKNOWN".equals(finding.outcome().name())
                          || "UNRESOLVABLE".equals(finding.outcome().name())
                          || "INCOMPATIBLE".equals(finding.outcome().name()))
              .map(CompatibilityFinding::message)
              .collect(Collectors.joining(" ")));
    }
    return plan.findings().stream()
        .collect(Collectors.toMap(CompatibilityFinding::artifactId, finding -> finding));
  }
}
