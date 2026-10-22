package dev.ward.domain.agent;

import java.util.Objects;

/**
 * What an integration run should do.
 *
 * @param remove whether to remove every Ward-owned integration file
 * @param guard what to do with the opt-in approved-artifact guard
 */
public record IntegrationRequest(boolean remove, GuardMode guard) {
  public static final IntegrationRequest INSTALL = new IntegrationRequest(false, GuardMode.KEEP);
  public static final IntegrationRequest REMOVE = new IntegrationRequest(true, GuardMode.KEEP);

  /**
   * Validates the guard mode.
   *
   * @throws NullPointerException if {@code guard} is missing
   */
  public IntegrationRequest {
    Objects.requireNonNull(guard, "guard");
  }
}
