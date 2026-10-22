package dev.ward.domain.gate;

import java.util.List;
import java.util.Set;

/** Stable configuration identifiers for the built-in quality gates. */
public final class SupportedQualityGates {
  public static final String TRACEABILITY = "openspec-junit-traceability";
  public static final List<String> IDS =
      List.of(
          "tests",
          "compile",
          "coverage",
          "spotbugs",
          "checkstyle",
          "pmd",
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
          TRACEABILITY,
          "command",
          "openspec",
          "openspec-archived");

  private static final Set<String> SPEC_IDS = Set.of("openspec", "openspec-archived");

  public static final List<String> LEGACY_IDS = List.of("architecture", "compliance");

  public static final String ZAP_TARGET_PLACEHOLDER = "<TARGET_URL>";

  private SupportedQualityGates() {}

  /** Specification validations, which run separately from code-quality checks when requested. */
  public static Set<String> specIds() {
    return SPEC_IDS;
  }
}
