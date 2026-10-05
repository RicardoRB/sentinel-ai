package dev.sentinel.domain.init;

import java.util.List;

/** Validated choices made by an initialization request. */
public record InitSelection(List<String> integrations, List<String> gates, String architecture) {
    public InitSelection {
        integrations = List.copyOf(integrations);
        gates = List.copyOf(gates);
    }

    public InitSelection(String integration, String gate) {
        this(List.of(integration), List.of(gate), null);
    }

    public InitSelection(List<String> integrations, String gate) {
        this(integrations, List.of(gate), null);
    }

    public InitSelection(List<String> integrations, List<String> gates) {
        this(integrations, gates, null);
    }
}
