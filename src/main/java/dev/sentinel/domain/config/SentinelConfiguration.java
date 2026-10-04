package dev.sentinel.domain.config;

import java.util.LinkedHashMap;
import java.util.Map;

/** Parsed {@code sentinel.toml}. Gates keep their declaration order. */
public record SentinelConfiguration(int version, Map<String, GateConfiguration> gates,
                                    Map<String, Profile> profiles) {

    public static final String FILE_NAME = "sentinel.toml";
    public static final int SUPPORTED_VERSION = 1;

    public SentinelConfiguration {
        gates = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(gates));
        profiles = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
    }

    public SentinelConfiguration(int version, Map<String, GateConfiguration> gates) {
        this(version, gates, Map.of());
    }

    public Map<String, GateConfiguration> enabledGates() {
        Map<String, GateConfiguration> enabled = new LinkedHashMap<>();
        gates.forEach((name, gate) -> {
            if (gate.enabled()) {
                enabled.put(name, gate);
            }
        });
        return enabled;
    }
}
