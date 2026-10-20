package dev.sentinel.application.init;

import dev.sentinel.domain.init.InitGateOption;
import java.util.ArrayList;
import java.util.List;

/** Gate choices that run through the project's Maven or Gradle executable. */
record BuildToolGates(boolean gradle, boolean available, String availability, String executable) {

  /** Gates that build, test, and analyze the project, in wizard order. */
  List<InitGateOption> analysisGates() {
    return List.of(
        option("tests", "Runs the project's tests.", "", "test"),
        option("compile", "Checks that the project compiles.", "", task("classes", "compile")),
        option(
            "coverage",
            "Enforces minimum test coverage.",
            gradle
                ? " (minimum coverage is configured in the Gradle build.)"
                : " (minimum coverage is configured in pom.xml.)",
            task("jacocoTestCoverageVerification", "jacoco:check")),
        option(
            "spotbugs",
            "Finds potential bugs through static analysis.",
            "",
            task("spotbugsMain", "spotbugs:check")),
        option(
            "checkstyle",
            "Checks source style and formatting rules.",
            "",
            task("checkstyleMain", "checkstyle:check")),
        option(
            "pmd",
            "Finds common Java design and implementation problems with PMD.",
            "",
            task("pmdMain", "pmd:check")),
        option("sonar", "Checks code quality, smells, and duplication.", "", "sonar"),
        option(
            "dependency-check",
            "Scans dependencies for known CVEs.",
            "",
            task("dependencyCheckAnalyze", "dependency-check:check")),
        archunit(),
        option(
            "mutation",
            "Measures test strength with mutation testing.",
            "",
            task("pitest", "pitest:mutationCoverage")),
        option(
            "format",
            "Checks formatting with Spotless and google-java-format.",
            "",
            task("spotlessCheck", "spotless:check")));
  }

  /** Gates that enforce build, license, compatibility, or custom policies, in wizard order. */
  List<InitGateOption> policyGates() {
    return List.of(
        option(
            "enforcer",
            "Enforces dependency and build rules with Maven Enforcer.",
            " (dependency and build rules are configured in pom.xml.)",
            task("check", "enforcer:enforce")),
        option(
            "license",
            "Checks dependency license compliance with the License Maven Plugin.",
            " (license policy is configured in pom.xml.)",
            task("licenseMain", "license:add-third-party")),
        apiCompat(),
        option(
            "command",
            "Runs a custom command from sentinel.toml.",
            " (customize the command in sentinel.toml after initialization.)",
            "test"));
  }

  // Gradle task options follow their task: test --tests <pattern>.
  private InitGateOption archunit() {
    final String description = "Checks architecture rules with ArchitectureTest.";
    final String note = " (runs only ArchitectureTest.)";
    return gradle
        ? option("archunit", description, note, "test", "--tests", "*ArchitectureTest")
        : option("archunit", description, note, "-Dtest=ArchitectureTest", "test");
  }

  // japicmp:cmp compares the packaged jar, so Maven packages it first.
  private InitGateOption apiCompat() {
    final String description = "Checks binary/API compatibility between versions with japicmp.";
    final String note = " (the comparison baseline is configured in pom.xml.)";
    return gradle
        ? option("api-compat", description, note, "japicmp")
        : option("api-compat", description, note, "-DskipTests", "package", "japicmp:cmp");
  }

  private String task(final String gradleTask, final String mavenTask) {
    return gradle ? gradleTask : mavenTask;
  }

  private InitGateOption option(
      final String id, final String description, final String note, final String... arguments) {
    final List<String> command = new ArrayList<>(List.of(arguments));
    command.addFirst(executable);
    return new InitGateOption(id, description, command, available, availability + note);
  }
}
