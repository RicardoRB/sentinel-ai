package dev.sentinel.domain.init;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Opinionated, immutable quality configurations used by {@code sentinel init}. */
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

  public String id() {
    return id;
  }

  public List<String> gates() {
    return gates;
  }

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

  public record ArchitectureExtras(
      boolean cycles, boolean noFieldInjection, boolean noStandardStreams) {}

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
