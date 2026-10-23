package dev.ward.infrastructure.config;

import dev.ward.domain.config.WardException;
import java.nio.file.Path;
import java.util.Set;
import org.tomlj.TomlTable;

/**
 * Parses the optional {@code root} of an OpenSpec gate: a non-blank relative path. Whether it stays
 * inside the repository is checked when the gate runs, since only then is the project known.
 */
final class TomlOpenSpecRoot {
  private static final String ROOT = "root";
  private static final Set<String> OPENSPEC_GATES = Set.of("openspec", "openspec-archived");

  private TomlOpenSpecRoot() {}

  static String parse(final String where, final TomlTable gate) {
    if (!gate.contains(ROOT)) {
      return null;
    }
    final String key = where + "." + ROOT;
    if (!OPENSPEC_GATES.contains(where.substring(where.lastIndexOf('.') + 1))) {
      throw new WardException("'" + key + "' is only supported for the openspec gates");
    }
    return relative(key, gate.isString(ROOT) ? gate.getString(ROOT) : null);
  }

  private static String relative(final String key, final String value) {
    if (value == null || value.isBlank()) {
      throw new WardException("'" + key + "' must be a non-blank relative path");
    }
    if (Path.of(value).isAbsolute()) {
      throw new WardException("'" + key + "' must be relative to the project root");
    }
    return value;
  }
}
