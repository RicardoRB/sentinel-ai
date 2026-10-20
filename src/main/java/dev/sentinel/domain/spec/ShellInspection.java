package dev.sentinel.domain.spec;

import java.util.List;

/**
 * What a shell command would do to the files the guard protects.
 *
 * @param inspectable whether the command text could be interpreted at all
 * @param archiveCommand whether it runs a recognized {@code openspec archive}
 * @param targets the paths it names as written, deleted, or moved
 */
public record ShellInspection(
    boolean inspectable, boolean archiveCommand, List<GuardTarget> targets) {

  /** Copies the targets. */
  public ShellInspection {
    targets = List.copyOf(targets);
  }

  /** Returns the inspection of a command that could not be interpreted. */
  public static ShellInspection uninspectable() {
    return new ShellInspection(false, false, List.of());
  }
}
