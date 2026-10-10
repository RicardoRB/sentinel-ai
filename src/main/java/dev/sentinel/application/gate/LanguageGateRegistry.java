package dev.sentinel.application.gate;

import dev.sentinel.domain.project.Language;
import java.util.List;
import java.util.Map;

/** Declarative ecosystem defaults; tools remain external and are never installed by Sentinel. */
public final class LanguageGateRegistry {
  private static final String TESTS = "tests";
  private static final String SEMGREP = "semgrep";
  private static final String GITLEAKS = "gitleaks";
  private static final String TRIVY = "trivy";
  private static final Map<Language, List<String>> DEFAULTS =
      Map.of(
          Language.JAVA,
              List.of(
                  "compile",
                  TESTS,
                  "coverage",
                  "spotbugs",
                  "checkstyle",
                  "sonar",
                  "dependency-check",
                  "archunit",
                  "mutation",
                  "format",
                  SEMGREP,
                  GITLEAKS,
                  "zap",
                  TRIVY,
                  "enforcer",
                  "license",
                  "api-compat"),
          Language.KOTLIN, List.of("compile", TESTS, GITLEAKS, SEMGREP, TRIVY),
          Language.TYPESCRIPT, List.of("command", TESTS, GITLEAKS, SEMGREP, TRIVY),
          Language.JAVASCRIPT, List.of("command", TESTS, GITLEAKS, SEMGREP, TRIVY),
          Language.PYTHON, List.of(TESTS, "command", GITLEAKS, SEMGREP, TRIVY),
          Language.GO, List.of(TESTS, "command", GITLEAKS, SEMGREP, TRIVY),
          Language.RUST, List.of(TESTS, "command", GITLEAKS, SEMGREP, TRIVY),
          Language.CSHARP, List.of("compile", TESTS, GITLEAKS, SEMGREP, TRIVY));

  public List<String> defaults(final Language language) {
    return DEFAULTS.getOrDefault(language, List.of());
  }
}
