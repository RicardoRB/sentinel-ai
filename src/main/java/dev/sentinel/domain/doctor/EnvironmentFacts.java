package dev.sentinel.domain.doctor;

import java.util.Map;
import java.util.Set;

/** Immutable environment observations used by setup and diagnostics policy. */
public record EnvironmentFacts(Set<String> availableExecutables, Map<String, String> values) {
    public EnvironmentFacts {
        availableExecutables = Set.copyOf(availableExecutables);
        values = Map.copyOf(values);
    }

    public boolean hasExecutable(String name) {
        return availableExecutables.contains(name);
    }
}
