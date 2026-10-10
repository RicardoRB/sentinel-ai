package dev.sentinel.infrastructure.config;

import dev.sentinel.domain.config.CommandLineTokenizer;
import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.tomlj.TomlArray;
import org.tomlj.TomlTable;

/** Parses one {@code [quality-gates.<id>]} table. */
final class TomlGateParser {
  private static final String COMMAND = "command";
  private static final String PROFILES = "profiles";

  private TomlGateParser() {}

  static GateConfiguration parse(final String where, final TomlTable gate) {
    final Set<String> profiles = profiles(where, gate);
    final boolean enabled = enabled(where, gate);
    if (!gate.contains(COMMAND)) {
      if (enabled) {
        throw new SentinelException("'" + where + ".command' is required for an enabled gate");
      }
      return new GateConfiguration(false, List.of(), profiles);
    }
    return new GateConfiguration(enabled, command(where, gate), profiles);
  }

  private static boolean enabled(final String where, final TomlTable gate) {
    if (!gate.contains("enabled")) {
      return true;
    }
    final Boolean value = gate.isBoolean("enabled") ? gate.getBoolean("enabled") : null;
    if (value == null) {
      throw new SentinelException("'" + where + ".enabled' must be true or false");
    }
    return value;
  }

  private static Set<String> profiles(final String where, final TomlTable gate) {
    if (!gate.contains(PROFILES)) {
      return Set.of(SentinelConfiguration.DEFAULT_PROFILE);
    }
    final String key = where + ".profiles";
    if (!gate.isArray(PROFILES)) {
      throw new SentinelException("'" + key + "' must be a non-empty array of non-blank strings");
    }
    final TomlArray array = gate.getArray(PROFILES);
    if (array == null || array.isEmpty()) {
      throw new SentinelException("'" + key + "' must not be empty");
    }
    final Set<String> profiles = new LinkedHashSet<>();
    for (int i = 0; i < array.size(); i++) {
      profiles.add(profile(array, i, key));
    }
    return profiles;
  }

  private static String profile(final TomlArray array, final int index, final String key) {
    final String profile = array.isString(index) ? array.getString(index) : null;
    if (profile == null || profile.isBlank()) {
      throw new SentinelException("'" + key + "' must contain only non-blank strings");
    }
    return profile.trim();
  }

  private static List<String> command(final String where, final TomlTable gate) {
    final List<String> command;
    if (gate.isString(COMMAND)) {
      command = commandString(where, gate);
    } else if (gate.isArray(COMMAND)) {
      command = commandArray(where, gate);
    } else {
      throw new SentinelException(
          "'" + where + ".command' must be a string or an array of strings");
    }
    if (command.isEmpty() || command.getFirst().isBlank()) {
      throw new SentinelException("'" + where + ".command' must not be empty");
    }
    return command;
  }

  private static List<String> commandString(final String where, final TomlTable gate) {
    final String value = gate.getString(COMMAND);
    if (value == null) {
      throw new SentinelException("'" + where + ".command' must be a string");
    }
    return CommandLineTokenizer.tokenize(value);
  }

  private static List<String> commandArray(final String where, final TomlTable gate) {
    final TomlArray array = gate.getArray(COMMAND);
    if (array == null) {
      throw new SentinelException("'" + where + ".command' must be an array of strings");
    }
    final List<String> command = new ArrayList<>(array.size());
    for (int i = 0; i < array.size(); i++) {
      if (!array.isString(i)) {
        throw new SentinelException("'" + where + ".command' array must contain only strings");
      }
      command.add(array.getString(i));
    }
    return command;
  }
}
