package dev.sentinel.infrastructure.config;

import dev.sentinel.domain.config.PartialGateConfiguration;
import dev.sentinel.domain.config.SentinelException;
import java.util.ArrayList;
import java.util.List;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/** Parses the optional {@code [quality-gates.<id>.partial]} table of one gate. */
final class TomlPartialParser {
  private static final String PARTIAL = "partial";
  private static final String INPUTS = "inputs";
  private static final String ARGS = "args";
  private static final String ITEM = "item";

  private TomlPartialParser() {}

  /** Returns the gate's partial settings, or {@code null} when it declares none. */
  static PartialGateConfiguration parse(final String where, final TomlTable gate) {
    if (!gate.contains(PARTIAL)) {
      return null;
    }
    final String key = where + "." + PARTIAL;
    final TomlTable table = gate.isTable(PARTIAL) ? gate.getTable(PARTIAL) : null;
    if (table == null) {
      throw new SentinelException("'" + key + "' must be a table");
    }
    if (table.contains(ARGS) && !table.contains(INPUTS)) {
      throw new SentinelException("'" + key + ".args' requires '" + key + ".inputs'");
    }
    return new PartialGateConfiguration(
        strings(table, key, INPUTS),
        strings(table, key, "config"),
        strings(table, key, ARGS),
        item(table, key),
        string(table, key, "separator"));
  }

  /** An {@code item} without a placeholder would pass the same text for every edited path. */
  private static String item(final TomlTable table, final String key) {
    final String item = string(table, key, ITEM);
    if (item != null && !item.contains("{path}") && !item.contains("{name}")) {
      throw new SentinelException("'" + key + ".item' must contain {path} or {name}");
    }
    return item;
  }

  private static List<String> strings(final TomlTable table, final String key, final String name) {
    if (!table.contains(name)) {
      return List.of();
    }
    final TomlArray array = table.isArray(name) ? table.getArray(name) : null;
    if (array == null) {
      throw new SentinelException("'" + key + "." + name + "' must be an array of strings");
    }
    final List<String> values = new ArrayList<>(array.size());
    for (int i = 0; i < array.size(); i++) {
      values.add(element(array, i, key + "." + name));
    }
    return values;
  }

  private static String element(final TomlArray array, final int index, final String key) {
    final String value = array.isString(index) ? array.getString(index) : null;
    if (value == null || value.isBlank()) {
      throw new SentinelException("'" + key + "' must contain only non-blank strings");
    }
    return value.trim();
  }

  private static String string(final TomlTable table, final String key, final String name) {
    if (!table.contains(name)) {
      return null;
    }
    if (!table.isString(name)) {
      throw new SentinelException("'" + key + "." + name + "' must be a string");
    }
    return table.getString(name);
  }
}
