package dev.sentinel.domain.init;

import java.util.List;
import java.util.Map;

/** Opinionated, immutable quality configurations used by {@code sentinel init}. */
public enum QualityPreset {
  STANDARD(
      "standard",
      List.of("compile", "tests", "format", "checkstyle", "pmd", "spotbugs", "coverage"),
      new PresetRules(
          List.of("category/java/quickstart.xml"),
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
          Map.of("IllegalCatch.allowedClassNames", "Throwable,Error"),
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
              "category/java/quickstart.xml",
              "category/java/bestpractices.xml",
              "rulesets/java/optimizations.xml/MethodArgumentCouldBeFinal",
              "rulesets/java/optimizations.xml/LocalVariableCouldBeFinal",
              "rulesets/java/typing.xml/UseExplicitTypes"),
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
  private final PresetRules rules;

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
