package dev.ward.infrastructure.config;

import dev.ward.domain.config.CommandLineTokenizer;
import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.PartialGateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardException;
import dev.ward.domain.gate.SupportedQualityGates;
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
    final PartialGateConfiguration partial = TomlPartialParser.parse(where, gate);
    final String root = TomlOpenSpecRoot.parse(where, gate);
    if (!gate.contains(COMMAND)) {
      if (enabled && where.endsWith("." + SupportedQualityGates.TRACEABILITY)) {
        return new GateConfiguration(true, List.of(), profiles, partial, root);
      }
      if (enabled) {
        throw new WardException("'" + where + ".command' is required for an enabled gate");
      }
      return new GateConfiguration(false, List.of(), profiles, partial, root);
    }
    return new GateConfiguration(enabled, command(where, gate), profiles, partial, root);
  }

  private static boolean enabled(final String where, final TomlTable gate) {
    if (!gate.contains("enabled")) {
      return true;
    }
    final Boolean value = gate.isBoolean("enabled") ? gate.getBoolean("enabled") : null;
    if (value == null) {
      throw new WardException("'" + where + ".enabled' must be true or false");
    }
    return value;
  }

  private static Set<String> profiles(final String where, final TomlTable gate) {
    if (!gate.contains(PROFILES)) {
      return Set.of(WardConfiguration.DEFAULT_PROFILE);
    }
    final String key = where + ".profiles";
    if (!gate.isArray(PROFILES)) {
      throw new WardException("'" + key + "' must be a non-empty array of non-blank strings");
    }
    final TomlArray array = gate.getArray(PROFILES);
    if (array == null || array.isEmpty()) {
      throw new WardException("'" + key + "' must not be empty");
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
      throw new WardException("'" + key + "' must contain only non-blank strings");
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
      throw new WardException("'" + where + ".command' must be a string or an array of strings");
    }
    if ((command.isEmpty() && !where.endsWith("." + SupportedQualityGates.TRACEABILITY))
        || (!command.isEmpty() && command.getFirst().isBlank())) {
      throw new WardException("'" + where + ".command' must not be empty");
    }
    return command;
  }

  private static List<String> commandString(final String where, final TomlTable gate) {
    final String value = gate.getString(COMMAND);
    if (value == null) {
      throw new WardException("'" + where + ".command' must be a string");
    }
    return CommandLineTokenizer.tokenize(value);
  }

  private static List<String> commandArray(final String where, final TomlTable gate) {
    final TomlArray array = gate.getArray(COMMAND);
    if (array == null) {
      throw new WardException("'" + where + ".command' must be an array of strings");
    }
    final List<String> command = new ArrayList<>(array.size());
    for (int i = 0; i < array.size(); i++) {
      if (!array.isString(i)) {
        throw new WardException("'" + where + ".command' array must contain only strings");
      }
      command.add(array.getString(i));
    }
    return command;
  }
}
