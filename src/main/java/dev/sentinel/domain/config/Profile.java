package dev.sentinel.domain.config;

import java.util.List;

/**
 * A named set of gates.
 *
 * @param name the profile name
 * @param gates the gate IDs it selects
 */
public record Profile(String name, List<String> gates) {
  /** Copies the gates. */
  public Profile {
    gates = List.copyOf(gates);
  }
}
