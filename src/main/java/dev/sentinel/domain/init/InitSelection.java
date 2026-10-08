package dev.sentinel.domain.init;

import java.util.List;

/** Validated choices made by an initialization request. */
public record InitSelection(List<String> integrations, List<String> gates, String architecture) {
  public InitSelection {
    integrations = List.copyOf(integrations);
    gates = List.copyOf(gates);
  }

  public InitSelection(final String integration, final String gate) {
    this(List.of(integration), List.of(gate), null);
  }

  public InitSelection(final List<String> integrations, final String gate) {
    this(integrations, List.of(gate), null);
  }

  public InitSelection(final List<String> integrations, final List<String> gates) {
    this(integrations, gates, null);
  }
}
