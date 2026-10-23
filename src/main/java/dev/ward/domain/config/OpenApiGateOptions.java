package dev.ward.domain.config;

import java.util.Objects;

/**
 * The OpenAPI options of an {@code openapi-*} gate.
 *
 * @param spec the project-relative OpenAPI file the gate checks
 * @param base the Git ref {@code openapi-compat} compares against
 */
public record OpenApiGateOptions(String spec, String base) {
  /** The base ref used when a gate declares none. */
  public static final String DEFAULT_BASE = "origin/main";

  /** Defaults {@code base} to {@link #DEFAULT_BASE}. */
  public OpenApiGateOptions {
    Objects.requireNonNull(spec, "spec");
    base = Objects.requireNonNullElse(base, DEFAULT_BASE);
  }
}
