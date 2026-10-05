package dev.sentinel.domain.policy;

import dev.sentinel.domain.gate.CheckReport;
import java.util.List;

/** Evaluates policy separately from gate execution. */
public final class PolicyEvaluator {
  public List<PolicyResult> evaluate(CheckReport report, boolean strict) {
    boolean allPassed = report.passed();
    return List.of(
        new PolicyResult(
            strict ? "strict" : "all-gates-pass",
            allPassed,
            allPassed ? "Policy passed." : "One or more quality gates failed."));
  }
}
