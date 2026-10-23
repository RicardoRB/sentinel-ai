package dev.ward.domain.agent;

import java.util.Objects;

/**
 * What an integration run should do.
 *
 * @param remove whether to remove every Ward-owned integration file
 * @param guard what to do with the opt-in approved-artifact guard
 * @param projectPath the {@code /}-separated path of the Ward project below the install root, or
 *     empty when the install root is the project root
 */
public record IntegrationRequest(boolean remove, GuardMode guard, String projectPath) {
  public static final IntegrationRequest INSTALL = new IntegrationRequest(false, GuardMode.KEEP);
  public static final IntegrationRequest REMOVE = new IntegrationRequest(true, GuardMode.KEEP);

  /** Creates a request for a project whose root is the install root. */
  public IntegrationRequest(final boolean remove, final GuardMode guard) {
    this(remove, guard, "");
  }

  /**
   * Validates the guard mode.
   *
   * @throws NullPointerException if {@code guard} or {@code projectPath} is missing
   */
  public IntegrationRequest {
    Objects.requireNonNull(guard, "guard");
    Objects.requireNonNull(projectPath, "projectPath");
  }

  /** Returns this request for the Ward project at {@code path} below the install root. */
  public IntegrationRequest forProject(final String path) {
    return new IntegrationRequest(remove, guard, path);
  }
}
