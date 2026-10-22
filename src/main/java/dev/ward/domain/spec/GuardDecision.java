package dev.ward.domain.spec;

import java.util.Objects;

/**
 * Whether an agent tool call may run.
 *
 * @param reason the refusal reason for the agent; empty when allowed
 */
public record GuardDecision(boolean allowed, String reason) {
  public static final GuardDecision ALLOWED = new GuardDecision(true, "");

  /**
   * Validates the reason.
   *
   * @throws NullPointerException if {@code reason} is missing
   */
  public GuardDecision {
    Objects.requireNonNull(reason, "reason");
  }

  /** Creates a refusal that tells the agent {@code reason}. */
  public static GuardDecision refused(final String reason) {
    return new GuardDecision(false, reason);
  }
}
