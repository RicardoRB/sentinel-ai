package dev.sentinel.domain.agent;

import java.util.Objects;

/**
 * What an agent integration should do: install or remove, and how Sentinel-owned OpenSpec guidance
 * participates.
 */
public record IntegrationRequest(Operation operation, Guidance guidance) {
  /** Whether integration files are written or removed. */
  public enum Operation {
    INSTALL,
    REMOVE
  }

  /** Which targets the request covers relative to the OpenSpec guidance file. */
  public enum Guidance {
    /** Only the agent's hook files. */
    EXCLUDED,
    /** The agent's hook files and the guidance file. */
    INCLUDED,
    /** Only the guidance file. */
    ONLY
  }

  public IntegrationRequest {
    Objects.requireNonNull(operation, "operation");
    Objects.requireNonNull(guidance, "guidance");
  }

  public static IntegrationRequest install(final Guidance guidance) {
    return new IntegrationRequest(Operation.INSTALL, guidance);
  }

  public static IntegrationRequest remove(final Guidance guidance) {
    return new IntegrationRequest(Operation.REMOVE, guidance);
  }

  public boolean removal() {
    return operation == Operation.REMOVE;
  }

  public boolean coversHooks() {
    return guidance != Guidance.ONLY;
  }

  public boolean coversGuidance() {
    return guidance != Guidance.EXCLUDED;
  }
}
