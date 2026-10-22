package dev.ward.domain.init;

/** Application callback used to obtain explicit consent for a compatibility update. */
@FunctionalInterface
public interface CompatibilityApproval {
  /** Returns whether the user approves updating the plugin {@code finding} describes. */
  boolean approve(CompatibilityFinding finding);
}
