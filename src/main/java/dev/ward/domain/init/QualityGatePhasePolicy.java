package dev.ward.domain.init;

import dev.ward.domain.gate.QualityGateDefinitions;
import java.util.List;

/** Pure policy for assigning init-generated quality gates to execution phases. */
public final class QualityGatePhasePolicy {
  public static final String DEFAULT = "default";
  public static final String PRE_COMMIT = "pre-commit";
  public static final String PRE_PUSH = "pre-push";
  public static final String CI = "ci";

  private QualityGatePhasePolicy() {}

  /** Returns ordered profile names for a selected gate. */
  public static List<String> profilesFor(final String gateId) {
    return switch (QualityGateDefinitions.find(gateId)
        .map(QualityGateDefinitions.Definition::phase)
        .orElse(QualityGateDefinitions.Phase.CI)) {
      case FAST -> List.of(DEFAULT, PRE_COMMIT, PRE_PUSH, CI);
      // Against HEAD it only sees uncommitted edits; pushed commits are openapi-compat's job.
      case DIFF -> List.of(DEFAULT, PRE_COMMIT);
      case LOCAL -> List.of(PRE_PUSH, CI);
      default -> List.of(CI);
    };
  }
}
