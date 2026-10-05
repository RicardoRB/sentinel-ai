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
          "compliance",
          "command");
  public static final List<String> LEGACY_IDS = List.of("architecture");

  private SupportedQualityGates() {}
}
