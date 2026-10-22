package dev.ward.domain.config;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One gate's table in {@code ward.toml}.
 *
 * @param enabled whether the gate runs
 * @param command the argument vector it runs
 * @param profiles the profiles it belongs to
 * @param partial how a per-edit check narrows it, or {@code null} when it always runs in full
 */
public record GateConfiguration(
    boolean enabled, List<String> command, Set<String> profiles, PartialGateConfiguration partial) {

  /** Creates a gate without a partial table. */
  public GateConfiguration(
      final boolean enabled, final List<String> command, final Set<String> profiles) {
    this(enabled, command, profiles, null);
  }

  /** Copies the command and profiles, keeping profile order. */
  public GateConfiguration {
    command = List.copyOf(command);
    profiles = Collections.unmodifiableSet(new LinkedHashSet<>(profiles));
  }

  /** Creates a gate in the {@code default} profile only. */
  public GateConfiguration(final boolean enabled, final List<String> command) {
    this(enabled, command, Set.of(WardConfiguration.DEFAULT_PROFILE));
  }
}
