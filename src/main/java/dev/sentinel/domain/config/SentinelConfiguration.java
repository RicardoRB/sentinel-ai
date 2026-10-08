package dev.sentinel.domain.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
}
