package dev.ward.infrastructure.init;

import dev.ward.domain.init.QualityPreset;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * The blocks init appends after the {@code plugins {}} block, in order. A block is added when its
 * gate is selected, its preset requirement is met, and the script does not already contain its
 * marker text, so running init again adds nothing.
 */
final class GradleBuildBlocks {
  static final String MARKER = "// Ward-managed quality gate configuration";

  private static final List<Block> BLOCKS =
      List.of(
          new Block(
              "enforcer",
              false,
              "checkstyleMain",
              "gradle-enforcer",
              "",
              (kotlin, preset) -> GradleQualityBlocks.enforcement(kotlin)),
          new Block(
              "archunit",
              false,
              "archunit-junit5",
              "archunit-junit5",
              GradleQualityBlocks.ARCHUNIT_VERSION,
              (kotlin, preset) -> GradleQualityBlocks.archunit(kotlin)),
          new Block(
              "dependency-check",
              false,
              "NVD_API_KEY",
              "dependency-check-nvd-key",
              "",
              (kotlin, preset) -> GradleQualityBlocks.dependencyCheck(kotlin)),
          new Block(
              "checkstyle",
              true,
              GradleQualityBlocks.CHECKSTYLE_CONFIG,
              "checkstyle-config",
              "",
              (kotlin, preset) -> GradleQualityBlocks.checkstyle(kotlin)),
          new Block(
              "pmd",
              true,
              GradleQualityBlocks.PMD_RULESET,
              "pmd-ruleset",
              "",
              (kotlin, preset) -> GradleQualityBlocks.pmd(kotlin)),
          new Block(
              "format",
              false,
              "spotless {",
              "spotless-google-java-format",
              "",
              (kotlin, preset) -> GradleQualityBlocks.spotless()),
          new Block(
              "coverage",
              false,
              GradleQualityBlocks.COVERAGE_TASK,
              "jacoco-coverage-rule",
              "",
              GradleQualityBlocks::coverage),
          new Block(
              "mutation",
              false,
              GradleQualityBlocks.PITEST_JUNIT5,
              "pitest-junit5",
              "",
              GradleQualityBlocks::pitest),
          new Block(
              "license",
              false,
              "licenseReport {",
              "license-policy",
              "",
              GradleLicensePolicy::block));

  private GradleBuildBlocks() {}

  static void append(
      final GradleBuildFileEdit edit,
      final Set<String> selected,
      final QualityPreset preset,
      final boolean kotlin) {
    for (final Block block : BLOCKS) {
      if (selected.contains(block.gate())
          && (preset != null || !block.needsPreset())
          && !edit.contains(block.present())) {
        edit.append(
            "\n" + MARKER + "\n" + block.body().apply(kotlin, preset),
            block.tool(),
            block.version());
      }
    }
  }

  /** {@code present} is text whose presence means the script already has this block. */
  private record Block(
      String gate,
      boolean needsPreset,
      String present,
      String tool,
      String version,
      BiFunction<Boolean, QualityPreset, String> body) {}
}
