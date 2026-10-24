package dev.ward.domain.gate;

import java.util.List;
import java.util.Optional;

/**
 * The built-in quality gates, one row per gate in configuration order. A new gate or tool adds its
 * row here; the gate ID list, phase policy, report names and learning policy read from it.
 */
public final class QualityGateDefinitions {
  private static final List<Definition> ALL =
      List.of(
          gate("tests", Phase.LOCAL),
          gate("compile", Phase.LOCAL),
          gate("coverage", Phase.LOCAL),
          new Definition("spotbugs", "SpotBugs", Phase.LOCAL, false),
          new Definition("checkstyle", "Checkstyle", Phase.FAST, true),
          new Definition("pmd", "PMD", Phase.FAST, true),
          gate("sonar", Phase.CI),
          new Definition("dependency-check", "OWASP Dependency-Check", Phase.CI, false),
          gate("archunit", Phase.LOCAL),
          gate("mutation", Phase.CI),
          gate("format", Phase.FAST),
          gate("semgrep", Phase.CI),
          new Definition("gitleaks", "Gitleaks", Phase.FAST, true),
          gate("zap", Phase.CI),
          gate("trivy", Phase.CI),
          gate("enforcer", Phase.LOCAL),
          gate("license", Phase.LOCAL),
          gate("api-compat", Phase.CI),
          gate(SupportedQualityGates.OPENAPI_LINT, Phase.FAST),
          gate(SupportedQualityGates.OPENAPI_DIFF, Phase.DIFF),
          gate(SupportedQualityGates.OPENAPI_COMPAT, Phase.LOCAL),
          gate(SupportedQualityGates.TRACEABILITY, Phase.CI),
          gate("command", Phase.LOCAL),
          gate("openspec", Phase.CI),
          gate("openspec-archived", Phase.CI));

  /** When a gate runs: on every edit, before a push, only on staged diffs, or in CI. */
  public enum Phase {
    FAST,
    LOCAL,
    DIFF,
    CI
  }

  /**
   * One built-in gate.
   *
   * @param id the gate ID used in {@code ward.toml}
   * @param displayName the tool name shown in reports
   * @param phase when the gate runs
   * @param learnable whether the gate's findings name specific rules that learning can track
   */
  public record Definition(String id, String displayName, Phase phase, boolean learnable) {}

  private QualityGateDefinitions() {}

  /** Returns every built-in gate in configuration order. */
  public static List<Definition> all() {
    return ALL;
  }

  /** Returns the definition of {@code id}, or empty for an unknown or legacy gate. */
  public static Optional<Definition> find(final String id) {
    return ALL.stream().filter(definition -> definition.id().equals(id)).findFirst();
  }

  private static Definition gate(final String id, final Phase phase) {
    return new Definition(id, id, phase, false);
  }
}
