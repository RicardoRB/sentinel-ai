package dev.sentinel.domain.gate;

import java.util.List;

/** Stable configuration identifiers for the built-in quality gates. */
public final class SupportedQualityGates {
  public static final List<String> IDS =
      List.of(
          "tests",
          "compile",
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
          "api-compat",
          "command");
  public static final List<String> LEGACY_IDS = List.of("architecture", "compliance");

  public static final String ZAP_TARGET_PLACEHOLDER = "<TARGET_URL>";

  private SupportedQualityGates() {}
}
