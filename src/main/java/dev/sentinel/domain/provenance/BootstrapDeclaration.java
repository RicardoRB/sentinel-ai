package dev.sentinel.domain.provenance;

import java.util.Optional;

/** Explicit reviewer-visible authorization for establishing the first live-spec baseline. */
public record BootstrapDeclaration(String value) {
  public BootstrapDeclaration {
    value = value == null ? "" : value.trim();
  }

  public boolean present() {
    return !value.isBlank();
  }

  public Optional<String> asOptional() {
    return present() ? Optional.of(value) : Optional.empty();
  }
}
