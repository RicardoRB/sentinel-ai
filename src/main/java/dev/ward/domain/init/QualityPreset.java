package dev.ward.domain.init;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Opinionated, immutable quality configurations used by {@code ward init}. */
public enum QualityPreset {
  STANDARD(
      "standard",
      List.of("compile", "tests", "format", "checkstyle", "pmd", "spotbugs", "coverage"),
      new PresetRules(
          List.of("rulesets/java/quickstart.xml"),
          List.of(
              "EqualsHashCode",
              "MissingSwitchDefault",
              "FallThrough",
              "EmptyCatchBlock",
              "IllegalCatch",
              "AvoidStarImport",
              "UnusedImports",
              "StringLiteralEquality",
              "OneStatementPerLine",
              "MultipleVariableDeclarations"),
          Map.of("IllegalCatch.illegalClassNames", "Error, Throwable"),
          "Default",
          "Medium",
          List.of("EI_EXPOSE_REP", "EI_EXPOSE_REP2"),
          Map.of("LINE", 0.70),
          List.of(
              "dependencyConvergence", "banDuplicatePomDependencyVersions", "requireMavenVersion"),
          null,
          new ArchitectureExtras(false, false, false))),
  STRICT(
      "strict",
      List.of(
          "compile",
          "tests",
          "format",
          "checkstyle",
          "pmd",
          "spotbugs",
          "coverage",
          "archunit",
          "enforcer",
          "mutation"),
      new PresetRules(
          List.of(
              "rulesets/java/quickstart.xml",
              "category/java/bestpractices.xml",
              "category/java/codestyle.xml/MethodArgumentCouldBeFinal",
              "category/java/codestyle.xml/LocalVariableCouldBeFinal",
              "category/java/codestyle.xml/UseExplicitTypes"),
          List.of(
              "EqualsHashCode",
              "MissingSwitchDefault",
              "FallThrough",
              "EmptyCatchBlock",
              "IllegalCatch",
              "AvoidStarImport",
              "UnusedImports",
              "StringLiteralEquality",
              "OneStatementPerLine",
              "MultipleVariableDeclarations",
              "CyclomaticComplexity",
              "MethodLength",
              "ParameterNumber",
              "FileLength",
              "MissingJavadocType",
              "MissingJavadocMethod",
              "MagicNumber",
              "HiddenField",
              "TypeName",
              "MethodName",
              "ParameterName",
              "LocalVariableName",
              "MemberName"),
          Map.of(
              "CyclomaticComplexity.max", "10",
              "MethodLength.max", "60",
              "ParameterNumber.max", "5",
              "FileLength.max", "500",
              "MissingJavadocType.scope", "public",
              "MissingJavadocMethod.scope", "public",
              "HiddenField.ignoreConstructorParameter", "true",
              "HiddenField.ignoreSetter", "true"),
          "Max",
          "Low",
          List.of(),
          Map.of("LINE", 0.85, "BRANCH", 0.75),
          List.of(
              "dependencyConvergence",
              "banDuplicatePomDependencyVersions",
              "requireMavenVersion",
              "requireUpperBoundDeps",
              "requirePluginVersions",
              "banDynamicVersions"),
          60,
          new ArchitectureExtras(true, true, true)));

  private final String id;
  private final List<String> gates;

  // Enum constants serialize by name, so their fields are never serialized.
  private final transient PresetRules rules;

  QualityPreset(final String id, final List<String> gates, final PresetRules rules) {
    this.id = id;
    this.gates = List.copyOf(gates);
    this.rules = rules;
  }

  /** Returns the preset ID used in {@code ward.toml} and on the command line. */
  public String id() {
    return id;
  }

  /** Returns the gates the preset enables, in configuration order. */
  public List<String> gates() {
    return gates;
  }

  /** Returns the tool rules the preset writes. */
  public PresetRules rules() {
    return rules;
  }

  /**
   * Returns the selected gates with this preset's selected gates first, in preset order, followed
   * by the other selected gates in their given order. Unselected preset gates are not added.
   */
  public List<String> orderSelected(final List<String> selected) {
    return Stream.concat(gates.stream().filter(selected::contains), selected.stream())
        .distinct()
        .toList();
  }

  /**
   * Returns this preset's gates followed by every explicit gate not already present, in the given
   * order. Explicit gates never remove preset gates.
   */
  public List<String> withExplicitGates(final List<String> explicit) {
    return Stream.concat(gates.stream(), explicit.stream()).distinct().toList();
  }

  /**
   * Optional rules added to the generated ArchUnit test.
   *
   * @param cycles whether packages must be free of cycles
   * @param noFieldInjection whether field injection is forbidden
   * @param noStandardStreams whether writing to System.out and System.err is forbidden
   */
  public record ArchitectureExtras(
      boolean cycles, boolean noFieldInjection, boolean noStandardStreams) {}

  /** The tool rules a preset writes into rule files and the build. */
  public record PresetRules(
      List<String> pmdRules,
      List<String> checkstyleModules,
      Map<String, String> checkstyleProperties,
      String spotbugsEffort,
      String spotbugsThreshold,
      List<String> spotbugsExcludes,
      Map<String, Double> jacocoMinimums,
      List<String> enforcerRules,
      Integer mutationThreshold,
      ArchitectureExtras architectureExtras) {
    /** Copies the collections. */
    public PresetRules {
      pmdRules = List.copyOf(pmdRules);
      checkstyleModules = List.copyOf(checkstyleModules);
      checkstyleProperties = Map.copyOf(checkstyleProperties);
      spotbugsExcludes = List.copyOf(spotbugsExcludes);
      jacocoMinimums = Map.copyOf(jacocoMinimums);
      enforcerRules = List.copyOf(enforcerRules);
    }
  }
}
