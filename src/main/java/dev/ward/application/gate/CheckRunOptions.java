package dev.ward.application.gate;

import java.util.List;
import java.util.Set;

/**
 * How a check selects and runs its gates.
 *
 * @param profiles the profiles to select; empty selects the {@code default} profile
 * @param listener receives progress as each gate starts and finishes
 * @param failFast whether to stop after the first failing gate
 * @param excludedGates gate IDs to leave out after profile selection
 */
public record CheckRunOptions(
    List<String> profiles,
    CheckProgressListener listener,
    boolean failFast,
    Set<String> excludedGates) {

  /** Copies the collections so later changes by the caller do not affect the run. */
  public CheckRunOptions {
    profiles = List.copyOf(profiles);
    excludedGates = Set.copyOf(excludedGates);
  }
}
