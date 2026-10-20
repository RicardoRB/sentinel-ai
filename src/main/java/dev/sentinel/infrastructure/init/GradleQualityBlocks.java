package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.init.QualityPreset;
import java.util.Map;
import java.util.TreeMap;

/**
 * Gradle extension blocks that make the plugins use what init generates: the preset rule files, a
 * coverage rule that fails the build, and JUnit 5 support for mutation testing. Each block is
 * rendered for the Groovy or the Kotlin DSL.
 */
final class GradleQualityBlocks {
  static final String CHECKSTYLE_CONFIG = "config/checkstyle.xml";
  static final String PMD_RULESET = "config/pmd-ruleset.xml";
  static final String COVERAGE_TASK = "jacocoTestCoverageVerification";
  static final String PITEST_JUNIT5 = "junit5PluginVersion";
  static final String PITEST_VERSION = "pitestVersion";
  static final String ARCHUNIT_VERSION = "1.3.0";
  private static final String PITEST_VERSION_VALUE = "1.30.1";
  private static final String PITEST_JUNIT5_VERSION = "1.2.3";
  private static final Map<String, Double> DEFAULT_MINIMUMS = Map.of("LINE", 0.80);
  private static final int LIMIT_LENGTH = 128;

  private GradleQualityBlocks() {}

  static String enforcement(final boolean kotlin) {
    return kotlin
        ? "tasks.named(\"check\") { dependsOn(\"checkstyleMain\") }"
        : "tasks.named('check') { dependsOn 'checkstyleMain' }";
  }

  static String archunit(final boolean kotlin) {
    final String coordinates = "com.tngtech.archunit:archunit-junit5:" + ARCHUNIT_VERSION;
    return kotlin
        ? "dependencies { testImplementation(\"" + coordinates + "\") }"
        : "dependencies { testImplementation '" + coordinates + "' }";
  }

  static String dependencyCheck(final boolean kotlin) {
    return "dependencyCheck { nvd { apiKey = System.getenv("
        + quote(kotlin, "NVD_API_KEY")
        + ") } }";
  }

  /**
   * Like the Maven plugins init configures, Checkstyle and PMD analyze main sources only, so the
   * {@code check} task does not apply production rules to tests.
   */
  static String checkstyle(final boolean kotlin) {
    return "checkstyle {\n    configFile = file("
        + quote(kotlin, CHECKSTYLE_CONFIG)
        + ")\n"
        + mainSourceSetOnly(kotlin)
        + "}\n"
        + GradlePathFilterBlock.render(kotlin, "checkstyleMain", "Checkstyle");
  }

  /** {@code ruleSets} is cleared so Gradle's default rule set does not run beside the preset's. */
  static String pmd(final boolean kotlin) {
    return "pmd {\n    ruleSetFiles = files("
        + quote(kotlin, PMD_RULESET)
        + ")\n    ruleSets = "
        + (kotlin ? "listOf()" : "[]")
        + "\n"
        + mainSourceSetOnly(kotlin)
        + "}\n"
        + GradlePathFilterBlock.render(kotlin, "pmdMain", "Pmd");
  }

  private static String mainSourceSetOnly(final boolean kotlin) {
    return kotlin
        ? "    sourceSets = listOf(project.sourceSets.main.get())\n"
        : "    sourceSets = [project.sourceSets.main]\n";
  }

  static String spotless() {
    return "spotless {\n    java {\n        googleJavaFormat()\n    }\n}";
  }

  /** One limit per counter, the preset's minimums or a line ratio of 0.80 without a preset. */
  static String coverage(final boolean kotlin, final QualityPreset preset) {
    final Map<String, Double> minimums =
        new TreeMap<>(preset == null ? DEFAULT_MINIMUMS : preset.rules().jacocoMinimums());
    final StringBuilder limits = new StringBuilder(LIMIT_LENGTH * minimums.size());
    minimums.forEach(
        (counter, minimum) ->
            limits
                .append("            limit {\n                counter = ")
                .append(quote(kotlin, counter))
                .append("\n                minimum = ")
                .append(kotlin ? "\"" + minimum + "\".toBigDecimal()" : minimum.toString())
                .append("\n            }\n"));
    // The Kotlin DSL reaches tasks through their type-safe accessor on tasks.
    return (kotlin ? "tasks." : "")
        + COVERAGE_TASK
        + " {\n    violationRules {\n        rule {\n"
        + limits
        + "        }\n    }\n}";
  }

  /** PIT needs its JUnit 5 plugin to find JUnit Jupiter tests. */
  static String pitest(final boolean kotlin, final QualityPreset preset) {
    final Integer threshold = preset == null ? null : preset.rules().mutationThreshold();
    return "pitest {\n    "
        + PITEST_VERSION
        + " = "
        + quote(kotlin, PITEST_VERSION_VALUE)
        + "\n    "
        + PITEST_JUNIT5
        + " = "
        + quote(kotlin, PITEST_JUNIT5_VERSION)
        + (threshold == null ? "" : "\n    mutationThreshold = " + threshold)
        + "\n}";
  }

  private static String quote(final boolean kotlin, final String value) {
    return kotlin ? "\"" + value + "\"" : "'" + value + "'";
  }
}
