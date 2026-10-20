package dev.sentinel.application.init;

import dev.sentinel.domain.init.QualityPreset;
import java.util.List;

/**
 * The preset and quality gates recorded in an existing {@code sentinel.toml}, used to pre-fill the
 * wizard when reconfiguring. {@code preset} is {@code null} for a custom configuration.
 */
public record PreviousChoices(QualityPreset preset, List<String> gates) {
  /** Copies the gates. */
  public PreviousChoices {
    gates = List.copyOf(gates);
  }
}
