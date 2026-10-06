package dev.sentinel.application.gate;

import dev.sentinel.domain.project.Language;
import java.util.List;
import java.util.Map;

/** Declarative ecosystem defaults; tools remain external and are never installed by Sentinel. */
public final class LanguageGateRegistry {
  private static final Map<Language, List<String>> DEFAULTS =
      Map.of(
          Language.JAVA,
              List.of(
                  "compile",
                  "tests",
                  "coverage",
                  "spotbugs",
                  "checkstyle",
                  "sonar",
                  "dependency-check",
                  "archunit",
                  "mutation",
                  "format",
                  "semgrep",
                  "gitleaks",
                  "zap",
                  "trivy",
                  "enforcer",
                  "license",
                  "api-compat"),
          Language.KOTLIN, List.of("compile", "tests", "gitleaks", "semgrep", "trivy"),
          Language.TYPESCRIPT, List.of("command", "tests", "gitleaks", "semgrep", "trivy"),
          Language.JAVASCRIPT, List.of("command", "tests", "gitleaks", "semgrep", "trivy"),
          Language.PYTHON, List.of("tests", "command", "gitleaks", "semgrep", "trivy"),
          Language.GO, List.of("tests", "command", "gitleaks", "semgrep", "trivy"),
          Language.RUST, List.of("tests", "command", "gitleaks", "semgrep", "trivy"),
          Language.CSHARP, List.of("compile", "tests", "gitleaks", "semgrep", "trivy"));

  public List<String> defaults(Language language) {
    return DEFAULTS.getOrDefault(language, List.of());
  }
}
