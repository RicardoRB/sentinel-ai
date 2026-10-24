package dev.ward.application.init;

import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import java.util.List;

/** Java's gates on Maven and Gradle: the shared JVM gates plus Java's analysis tools. */
final class JavaGates implements SourceLanguageGates<BuildToolGates> {
  private static final List<String> STANDARD =
      List.of("compile", "tests", "format", "checkstyle", "pmd", "spotbugs", "coverage");
  private static final List<String> STRICT =
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
          "mutation");

  @Override
  public List<InitGateOption> analysisGates(final BuildToolGates jvm) {
    return List.of(
        jvm.tests(),
        jvm.compile(),
        jvm.coverage(),
        jvm.tool(
            "spotbugs",
            "Finds potential bugs through static analysis.",
            "spotbugsMain",
            "spotbugs:check"),
        jvm.tool(
            "checkstyle",
            "Checks source style and formatting rules.",
            "checkstyleMain",
            "checkstyle:check"),
        jvm.tool(
            "pmd",
            "Finds common Java design and implementation problems with PMD.",
            "pmdMain",
            "pmd:check"),
        jvm.sonar(),
        jvm.dependencyCheck(),
        jvm.archunit(),
        jvm.mutation(),
        jvm.tool(
            "format",
            "Checks formatting with Spotless and google-java-format.",
            "spotlessCheck",
            "spotless:check"));
  }

  @Override
  public List<String> presetGates(final QualityPreset preset) {
    return preset == QualityPreset.STRICT ? STRICT : STANDARD;
  }
}
