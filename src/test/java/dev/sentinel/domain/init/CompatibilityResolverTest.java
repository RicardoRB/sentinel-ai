package dev.sentinel.domain.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CompatibilityResolverTest {
  private static final String ARTIFACT = "demo-plugin";
  private static final Project PROJECT =
      new Project(Path.of("."), Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  private static final CompatibilityResolver RESOLVER = new CompatibilityResolver();

  @Test
  void selectsHighestCompatibleVersionForMissingPlugin() {
    final CompatibilityPlan plan =
        RESOLVER.resolve(
            snapshot(17, Optional.of("3.8.8"), Optional.empty(), Map.of()),
            Map.of(
                ARTIFACT,
                List.of(
                    candidate(ARTIFACT, "1.0.0", 8, "3.6"),
                    candidate(ARTIFACT, "2.0.0", 11, "3.8"))),
            List.of(ARTIFACT));

    assertThat(plan.findings())
        .singleElement()
        .satisfies(
            finding -> {
              assertThat(finding.selectedVersion()).isEqualTo("2.0.0");
              assertThat(finding.outcome()).isEqualTo(CompatibilityOutcome.COMPATIBLE);
            });
  }

  @Test
  void recommendsUpgradeForIncompatibleExistingPlugin() {
    final CompatibilityPlan plan =
        RESOLVER.resolve(
            snapshot(17, Optional.of("3.8.8"), Optional.empty(), Map.of(ARTIFACT, "0.1.0")),
            Map.of(ARTIFACT, List.of(candidate(ARTIFACT, "2.0.0", 11, "3.8"))),
            List.of(ARTIFACT));

    assertThat(plan.changes())
        .singleElement()
        .extracting(CompatibilityFinding::selectedVersion)
        .isEqualTo("2.0.0");
    assertThat(plan.findings())
        .singleElement()
        .extracting(CompatibilityFinding::outcome)
        .isEqualTo(CompatibilityOutcome.UPGRADE_RECOMMENDED);
  }

  @Test
  void reportsUnknownWhenMavenVersionIsUnavailable() {
    final CompatibilityPlan plan =
        RESOLVER.resolve(
            snapshot(17, Optional.empty(), Optional.empty(), Map.of()),
            Map.of(ARTIFACT, List.of(candidate(ARTIFACT, "1.0.0", 8, "3.6"))),
            List.of(ARTIFACT));

    assertThat(plan.hasUnresolvableFindings()).isTrue();
    assertThat(plan.findings())
        .singleElement()
        .extracting(CompatibilityFinding::outcome)
        .isEqualTo(CompatibilityOutcome.UNKNOWN);
  }

  private static CompatibilityProjectSnapshot snapshot(
      final int java,
      final Optional<String> maven,
      final Optional<Integer> release,
      final Map<String, String> plugins) {
    return new CompatibilityProjectSnapshot(
        PROJECT, new ToolchainSnapshot(java, maven, release, Framework.NONE), plugins);
  }

  private static ManagedPluginCandidate candidate(
      final String artifact,
      final String version,
      final int minimumJava,
      final String minimumMaven) {
    return new ManagedPluginCandidate(artifact, version, minimumJava, 25, minimumMaven, "plugin");
  }
}
