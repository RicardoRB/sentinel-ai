package dev.sentinel.domain.config;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure decision model for selecting and narrowing gates for an edit. */
public final class PartialCheckPlan {
  private static final String PATHS = "{paths}";
  private static final String PATH = "{path}";
  private static final String NAME = "{name}";

  private final Map<String, GatePlan> gates;

  /** How a partial check runs one gate. */
  public enum Scope {
    SKIP,
    FULL,
    NARROW
  }

  /** The decision for one gate; {@code args} are appended to its command when narrowed. */
  public record GatePlan(String gate, Scope scope, List<String> args) {
    /** Copies the arguments. */
    public GatePlan {
      args = List.copyOf(args);
    }
  }

  private PartialCheckPlan(final Map<String, GatePlan> gates) {
    this.gates = Collections.unmodifiableMap(new LinkedHashMap<>(gates));
  }

  /**
   * Decides, for each configured gate, whether the edited paths skip it, narrow it, or run it in
   * full. Without paths every gate runs in full.
   */
  public static PartialCheckPlan create(
      final Path root,
      final Map<String, GateConfiguration> configurations,
      final List<Path> editedPaths,
      final PathExistence existence) {
    final List<EditedPath> paths = normalize(root, editedPaths, existence);
    final boolean noPaths = editedPaths == null || editedPaths.isEmpty();
    final Map<String, GatePlan> plans = new LinkedHashMap<>();
    configurations.forEach(
        (name, configuration) -> plans.put(name, decide(name, configuration, paths, noPaths)));
    return new PartialCheckPlan(plans);
  }

  /** Returns the decision for each gate, in configuration order. */
  public Map<String, GatePlan> gates() {
    return gates;
  }

  /** Returns how many gates the plan handles with {@code scope}. */
  public long count(final Scope scope) {
    return gates.values().stream().filter(plan -> plan.scope() == scope).count();
  }

  private static GatePlan decide(
      final String name,
      final GateConfiguration configuration,
      final List<EditedPath> paths,
      final boolean noPaths) {
    final PartialGateConfiguration partial = configuration.partial();
    if (partial == null || noPaths) {
      return new GatePlan(name, Scope.FULL, List.of());
    }
    final List<EditedPath> relevant =
        paths.stream().filter(path -> matches(partial.inputs(), path.relative())).toList();
    if (paths.stream().anyMatch(path -> matches(partial.config(), path.relative()))) {
      return new GatePlan(name, Scope.FULL, List.of());
    }
    if (relevant.isEmpty()) {
      return new GatePlan(name, Scope.SKIP, List.of());
    }
    final List<EditedPath> existing = relevant.stream().filter(EditedPath::exists).toList();
    if (partial.args().isEmpty() || existing.isEmpty()) {
      return new GatePlan(name, Scope.FULL, List.of());
    }
    return new GatePlan(name, Scope.NARROW, render(partial, existing));
  }

  private static List<String> render(
      final PartialGateConfiguration partial, final List<EditedPath> paths) {
    final String item = partial.item();
    final List<String> items =
        paths.stream()
            .map(path -> item.replace(PATH, path.relative()).replace(NAME, path.name()))
            .toList();
    final List<String> rendered = new ArrayList<>();
    for (final String argument : partial.args()) {
      if (argument.contains(PATHS)) {
        rendered.addAll(substituted(argument, items, partial.separator()));
      } else {
        rendered.add(argument);
      }
    }
    return rendered;
  }

  /** One argument per item, or one argument joining the items with {@code separator}. */
  private static List<String> substituted(
      final String argument, final List<String> items, final String separator) {
    if (separator == null) {
      return items.stream().map(value -> argument.replace(PATHS, value)).toList();
    }
    return List.of(argument.replace(PATHS, String.join(separator, items)));
  }

  private static List<EditedPath> normalize(
      final Path root, final List<Path> paths, final PathExistence existence) {
    if (paths == null) {
      return List.of();
    }
    final Path absoluteRoot = existence.canonical(root);
    return paths.stream()
        .map(existence::canonical)
        .distinct()
        .filter(path -> path.startsWith(absoluteRoot))
        .map(
            path ->
                new EditedPath(
                    absoluteRoot.relativize(path).toString().replace('\\', '/'),
                    String.valueOf(path.getFileName()),
                    existence.exists(path)))
        .toList();
  }

  private static boolean matches(final List<String> patterns, final String path) {
    return patterns.stream().anyMatch(pattern -> PathPattern.of(pattern).matches(path));
  }

  private record EditedPath(String relative, String name, boolean exists) {}
}
