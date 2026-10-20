package dev.sentinel.domain.provenance;

import java.util.Optional;

/** Explicit reviewer-visible authorization for establishing the first live-spec baseline. */
public record BootstrapDeclaration(String value) {
  /** Trims the value; a missing value means no declaration. */
  public BootstrapDeclaration {
    value = value == null ? "" : value.trim();
  }

  /** Returns whether a bootstrap reason was declared. */
  public boolean present() {
    return !value.isBlank();
  }

  /** Returns the declared reason, or empty when none was given. */
  public Optional<String> asOptional() {
    return present() ? Optional.of(value) : Optional.empty();
  }
}
