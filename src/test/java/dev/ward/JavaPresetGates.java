package dev.ward;

import dev.ward.domain.init.QualityPreset;
import java.util.List;

/** The Java gate sets the quality-presets spec defines, for tests that expect them. */
public final class JavaPresetGates {
  public static final List<String> STANDARD =
      List.of("compile", "tests", "format", "checkstyle", "pmd", "spotbugs", "coverage");
  public static final List<String> STRICT =
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

  private JavaPresetGates() {}

  /** Returns the Java gate set of {@code preset}. */
  public static List<String> of(final QualityPreset preset) {
    return preset == QualityPreset.STRICT ? STRICT : STANDARD;
  }
}
