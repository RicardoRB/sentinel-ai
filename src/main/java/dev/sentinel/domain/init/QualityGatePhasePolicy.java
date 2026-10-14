package dev.sentinel.domain.init;

import java.util.List;
import java.util.Set;

/** Pure policy for assigning init-generated quality gates to execution phases. */
public final class QualityGatePhasePolicy {
  public static final String DEFAULT = "default";
  public static final String PRE_COMMIT = "pre-commit";
  public static final String PRE_PUSH = "pre-push";
  public static final String CI = "ci";
  private static final Set<String> FAST_GATES =
      Set.of("format", "compile", "checkstyle", "pmd", "gitleaks");
  private static final Set<String> LOCAL_GATES =
      Set.of("tests", "coverage", "spotbugs", "archunit", "enforcer", "license", "command");

  private QualityGatePhasePolicy() {}

  /** Returns ordered profile names for a selected gate. */
  public static List<String> profilesFor(final String gateId) {
    if (FAST_GATES.contains(gateId)) {
      return List.of(DEFAULT, PRE_COMMIT, PRE_PUSH, CI);
    }
    return LOCAL_GATES.contains(gateId) ? List.of(PRE_PUSH, CI) : List.of(CI);
  }
}
