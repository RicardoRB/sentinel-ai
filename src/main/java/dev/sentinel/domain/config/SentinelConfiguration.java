package dev.sentinel.domain.config;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parsed {@code sentinel.toml}. Gates keep their declaration order. */
public record SentinelConfiguration(
    int version, Map<String, GateConfiguration> gates, String preset) {

  public static final String FILE_NAME = "sentinel.toml";
  public static final int SUPPORTED_VERSION = 1;
  public static final String DEFAULT_PROFILE = "default";

  public SentinelConfiguration {
    gates = Collections.unmodifiableMap(new LinkedHashMap<>(gates));
  }

  public SentinelConfiguration(final int version, final Map<String, GateConfiguration> gates) {
    this(version, gates, null);
  }

  public Set<String> profileNames() {
    final Set<String> names = new LinkedHashSet<>();
    gates.values().forEach(gate -> names.addAll(gate.profiles()));
    return Collections.unmodifiableSet(names);
  }

  public Map<String, GateConfiguration> enabledGates() {
    final Map<String, GateConfiguration> enabled = new LinkedHashMap<>();
    gates.forEach(
        (name, gate) -> {
          if (gate.enabled()) {
            enabled.put(name, gate);
          }
        });
    return enabled;
  }

  /**
   * Returns the gates belonging to any requested profile, or to {@code default} when none is
   * requested.
   *
   * @throws SentinelException when a name is blank or a requested profile is unknown
   */
  public SentinelConfiguration forProfiles(final List<String> requested) {
    final List<String> names =
        requested == null || requested.isEmpty() ? List.of(DEFAULT_PROFILE) : requested;
    if (names.stream().anyMatch(name -> name == null || name.isBlank())) {
      throw new SentinelException("Profile names must not be blank.");
    }
    final Set<String> selectedNames = new LinkedHashSet<>(names);
    requireKnownProfiles(selectedNames);
    final Map<String, GateConfiguration> selected = new LinkedHashMap<>();
    gates.forEach(
        (gate, config) -> {
          if (config.profiles().stream().anyMatch(selectedNames::contains)) {
            selected.put(gate, config);
          }
        });
    return new SentinelConfiguration(version, selected, preset);
  }

  /** Returns only the gates whose IDs are listed, keeping declaration order. */
  public SentinelConfiguration onlyGates(final Collection<String> ids) {
    final Map<String, GateConfiguration> selected = new LinkedHashMap<>();
    gates.forEach(
        (gate, config) -> {
          if (ids.contains(gate)) {
            selected.put(gate, config);
          }
        });
    return new SentinelConfiguration(version, selected, preset);
  }

  /** Returns the configuration without the listed gates, keeping declaration order. */
  public SentinelConfiguration withoutGates(final Collection<String> ids) {
    final Map<String, GateConfiguration> remaining = new LinkedHashMap<>(gates);
    remaining.keySet().removeAll(ids);
    return new SentinelConfiguration(version, remaining, preset);
  }

  private void requireKnownProfiles(final Set<String> selectedNames) {
    if (selectedNames.contains(DEFAULT_PROFILE) && !profileNames().contains(DEFAULT_PROFILE)) {
      throw new SentinelException(
          "Nothing belongs to the default profile; pass --profile or add default to a gate's profiles.");
    }
    final Set<String> unknown = new LinkedHashSet<>(selectedNames);
    unknown.removeAll(profileNames());
    if (!unknown.isEmpty()) {
      throw new SentinelException(
          "Unknown profiles " + unknown + ". Available profiles: " + profileNames());
    }
  }
}
