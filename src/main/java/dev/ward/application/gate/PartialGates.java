package dev.ward.application.gate;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.PartialCheckPlan;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateScope;
import dev.ward.domain.gate.QualityGate;
import dev.ward.domain.gate.SkippedQualityGate;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Applies a {@link PartialCheckPlan}: narrowed commands, skipped gates, and reported scopes. */
final class PartialGates {
  static final String NOT_AFFECTED = "Not affected by the edited files";

  private PartialGates() {}

  /**
   * Creates the selected gates in configuration order. A narrowed gate runs its command followed by
   * the plan's arguments, and an enabled gate the plan skips runs nothing.
   */
  static List<QualityGate> create(
      final WardConfiguration selected,
      final PartialCheckPlan plan,
      final QualityGateFactory factory,
      final Project project) {
    final Map<String, GateConfiguration> executed = new LinkedHashMap<>();
    selected
        .gates()
        .forEach(
            (name, gate) -> {
              if (!skipped(name, gate, plan)) {
                executed.put(name, narrowed(gate, plan.gates().get(name)));
              }
            });
    final Iterator<QualityGate> created =
        factory
            .create(new WardConfiguration(selected.version(), executed, selected.preset()), project)
            .iterator();
    final List<QualityGate> gates = new ArrayList<>(selected.gates().size());
    selected
        .gates()
        .forEach(
            (name, gate) ->
                gates.add(
                    skipped(name, gate, plan)
                        ? new SkippedQualityGate(name, NOT_AFFECTED)
                        : created.next()));
    return gates;
  }

  /** Labels each result with the plan's decision and records the edited paths. */
  static CheckReport scoped(
      final CheckReport report, final PartialCheckPlan plan, final List<Path> paths) {
    final List<GateResult> results =
        report.results().stream()
            .map(result -> result.withScope(scope(plan.gates().get(result.name()))))
            .toList();
    return report.withResults(results).withPartialPaths(reported(report.project().root(), paths));
  }

  private static boolean skipped(
      final String name, final GateConfiguration gate, final PartialCheckPlan plan) {
    return gate.enabled() && plan.gates().get(name).scope() == PartialCheckPlan.Scope.SKIP;
  }

  private static GateConfiguration narrowed(
      final GateConfiguration gate, final PartialCheckPlan.GatePlan plan) {
    if (plan.scope() != PartialCheckPlan.Scope.NARROW) {
      return gate;
    }
    return new GateConfiguration(
        gate.enabled(),
        Stream.concat(gate.command().stream(), plan.args().stream()).toList(),
        gate.profiles(),
        gate.partial());
  }

  private static GateScope scope(final PartialCheckPlan.GatePlan plan) {
    return switch (plan.scope()) {
      case SKIP -> GateScope.SKIPPED;
      case NARROW -> GateScope.NARROWED;
      case FULL -> GateScope.FULL;
    };
  }

  /** Paths inside the project are reported root-relative with {@code /}, others as given. */
  private static List<String> reported(final Path root, final List<Path> paths) {
    final Path absoluteRoot = root.toAbsolutePath().normalize();
    return paths.stream()
        .map(path -> path.toAbsolutePath().normalize())
        .distinct()
        .map(
            path ->
                path.startsWith(absoluteRoot)
                    ? absoluteRoot.relativize(path).toString().replace('\\', '/')
                    : path.toString())
        .toList();
  }
}
