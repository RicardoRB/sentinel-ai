package dev.ward.application.init;

import dev.ward.domain.gate.SupportedQualityGates;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Project;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The {@code [quality-gates.<id>.partial]} table init writes for a gate whose tool can be scoped to
 * edited files. Each narrowing form was verified against the real tool: a form the tool does not
 * understand analyzes nothing and passes.
 */
final class PartialCheckDefaults {
  private static final List<String> JAVA_SOURCES = List.of("src/**/*.java");
  private static final List<String> MAVEN_BUILD = List.of("pom.xml");
  private static final List<String> GRADLE_BUILD = List.of("build.gradle*", "settings.gradle*");
  private static final String GRADLE_PATHS = "-PwardPaths={paths}";
  private static final String COMMA = ",";

  private static final Map<String, TableFactory> TABLES =
      Map.of(
          "checkstyle",
          PartialCheckDefaults::checkstyle,
          "pmd",
          PartialCheckDefaults::pmd,
          "format",
          (gradle, preset, build) -> format(gradle, build),
          "semgrep",
          (gradle, preset, build) ->
              new Table(List.of("**"), List.of(".semgrep*"), List.of("{paths}"), null, null));

  /** The table's keys; {@code item} and {@code separator} are omitted when {@code null}. */
  record Table(
      List<String> inputs, List<String> config, List<String> args, String item, String separator) {
    Table {
      inputs = List.copyOf(inputs);
      config = List.copyOf(config);
      args = List.copyOf(args);
    }

    private static Table full(final List<String> inputs, final List<String> config) {
      return new Table(inputs, config, List.of(), null, null);
    }
  }

  /** Builds a gate's table for Maven or Gradle, with or without a preset. */
  @FunctionalInterface
  private interface TableFactory {
    Table create(boolean gradle, QualityPreset preset, List<String> build);
  }

  private PartialCheckDefaults() {}

  static Optional<Table> forGate(
      final String gate, final Project project, final QualityPreset preset) {
    final boolean gradle = project.buildTool() == BuildTool.GRADLE;
    final List<String> build = gradle ? GRADLE_BUILD : MAVEN_BUILD;
    return Optional.ofNullable(TABLES.get(gate))
        .map(factory -> factory.create(gradle, preset, build));
  }

  /** Returns the gates that have a partial table, for the gate contract test. */
  static Set<String> gates() {
    return TABLES.keySet();
  }

  /**
   * The OpenAPI lint and diff gates always analyze the whole spec, so editing it runs them in full
   * and other edits skip them.
   */
  static Optional<Table> forOpenApi(final String gate, final String spec) {
    if (spec == null
        || !SupportedQualityGates.OPENAPI_LINT.equals(gate)
            && !SupportedQualityGates.OPENAPI_DIFF.equals(gate)) {
      return Optional.empty();
    }
    return Optional.of(Table.full(List.of(spec), List.of()));
  }

  /** Checkstyle's {@code includes} matches source-relative paths, so only the file name works. */
  private static Table checkstyle(
      final boolean gradle, final QualityPreset preset, final List<String> build) {
    final List<String> config = withRuleFile(preset, "config/checkstyle.xml", build);
    if (gradle) {
      return gradleFiltered(preset, config);
    }
    return new Table(
        JAVA_SOURCES, config, List.of("-Dcheckstyle.includes={paths}"), "**/{name}", COMMA);
  }

  /** The Maven PMD plugin has no property for {@code includes}; it runs in full, cached. */
  private static Table pmd(
      final boolean gradle, final QualityPreset preset, final List<String> build) {
    final List<String> config = withRuleFile(preset, "config/pmd-ruleset.xml", build);
    return gradle ? gradleFiltered(preset, config) : Table.full(JAVA_SOURCES, config);
  }

  /**
   * Spotless matches {@code spotlessFiles} against absolute paths. Gradle's single-file mode
   * applies formatting instead of checking it, so Gradle runs in full.
   */
  private static Table format(final boolean gradle, final List<String> build) {
    final List<String> config = gradle ? List.of(build.getFirst()) : build;
    if (gradle) {
      return Table.full(JAVA_SOURCES, config);
    }
    return new Table(JAVA_SOURCES, config, List.of("-DspotlessFiles={paths}"), ".*/{path}", COMMA);
  }

  /** Only the Gradle blocks init writes with a preset read {@code wardPaths}. */
  private static Table gradleFiltered(final QualityPreset preset, final List<String> config) {
    if (preset == null) {
      return Table.full(JAVA_SOURCES, config);
    }
    return new Table(JAVA_SOURCES, config, List.of(GRADLE_PATHS), null, COMMA);
  }

  private static List<String> withRuleFile(
      final QualityPreset preset, final String ruleFile, final List<String> build) {
    final List<String> config = new ArrayList<>(build.size() + 1);
    if (preset != null) {
      config.add(ruleFile);
    }
    config.addAll(build);
    return config;
  }
}
