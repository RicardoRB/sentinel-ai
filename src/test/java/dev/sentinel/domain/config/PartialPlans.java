package dev.sentinel.domain.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds one-gate partial check plans for the plan tests. */
final class PartialPlans {
  static final Path ROOT = Path.of("/work/shop").toAbsolutePath();
  static final String GATE = "checkstyle";
  static final List<String> JAVA = List.of("src/**/*.java");
  static final List<String> RULES = List.of("config/checkstyle.xml");
  static final List<String> INCLUDES = List.of("-Dcheckstyle.includes={paths}");
  static final String ORDER = "src/main/java/com/acme/Order.java";
  static final PathExistence EVERYTHING_EXISTS = path -> true;

  private PartialPlans() {}

  static PartialCheckPlan.GatePlan plan(final GateConfiguration gate, final String... paths) {
    return PartialCheckPlan.create(
            ROOT,
            Map.of(GATE, gate),
            List.of(paths).stream().map(ROOT::resolve).toList(),
            EVERYTHING_EXISTS)
        .gates()
        .get(GATE);
  }

  static PartialCheckPlan.GatePlan full() {
    return new PartialCheckPlan.GatePlan(GATE, PartialCheckPlan.Scope.FULL, List.of());
  }

  static PartialCheckPlan.GatePlan narrowed(final String... args) {
    return new PartialCheckPlan.GatePlan(GATE, PartialCheckPlan.Scope.NARROW, List.of(args));
  }

  static GateConfiguration gate(final PartialGateConfiguration partial) {
    return new GateConfiguration(true, List.of("tool"), Set.of("default"), partial);
  }

  static PartialGateConfiguration partial(
      final List<String> inputs,
      final List<String> config,
      final List<String> args,
      final String item,
      final String separator) {
    return new PartialGateConfiguration(inputs, config, args, item, separator);
  }
}
