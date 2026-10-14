package dev.sentinel.domain.init;

/** Application callback used to obtain explicit consent for a compatibility update. */
@FunctionalInterface
public interface CompatibilityApproval {
  boolean approve(CompatibilityFinding finding);
}
