package dev.ward.infrastructure.config;

import dev.ward.domain.config.OpenApiGateOptions;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.SupportedQualityGates;
import org.tomlj.TomlTable;

/** Parses the {@code spec} and {@code base} options of an {@code openapi-*} gate table. */
final class TomlOpenApiParser {
  private TomlOpenApiParser() {}

  /**
   * Returns the OpenAPI options of an {@code openapi-*} gate, or {@code null} for other gates and
   * for a disabled gate without a {@code spec}.
   */
  static OpenApiGateOptions parse(final String where, final TomlTable gate, final boolean enabled) {
    if (!SupportedQualityGates.openApiIds().contains(where.substring(where.lastIndexOf('.') + 1))) {
      return null;
    }
    final String spec = string(where, gate, "spec");
    if (spec == null) {
      if (!enabled) {
        return null;
      }
      throw new WardException("'" + where + ".spec' is required for an enabled OpenAPI gate");
    }
    return new OpenApiGateOptions(spec, string(where, gate, "base"));
  }

  private static String string(final String where, final TomlTable gate, final String name) {
    if (!gate.contains(name)) {
      return null;
    }
    final String value = gate.isString(name) ? gate.getString(name) : null;
    if (value == null || value.isBlank()) {
      throw new WardException("'" + where + "." + name + "' must be a non-blank string");
    }
    return value.trim();
  }
}
