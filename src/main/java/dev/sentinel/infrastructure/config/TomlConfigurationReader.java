package dev.sentinel.infrastructure.config;

import dev.sentinel.domain.config.CommandLineTokenizer;
import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelConfigurationReader;
import dev.sentinel.domain.config.SentinelException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

public class TomlConfigurationReader implements SentinelConfigurationReader {

  private static final String GATES_TABLE = "quality-gates";

  @Override
  public SentinelConfiguration read(Path file) {
    if (!Files.isRegularFile(file)) {
      throw new SentinelException(
          "Configuration file not found: " + file + ". Run 'sentinel init' first.");
    }
    TomlParseResult toml;
    try {
      toml = Toml.parse(file);
    } catch (IOException e) {
      throw new SentinelException("Could not read " + file + ": " + e.getMessage(), e);
    }
    if (toml.hasErrors()) {
      throw new SentinelException("Invalid TOML in " + file + ": " + toml.errors().getFirst());
    }
    return parse(toml);
  }

  SentinelConfiguration parse(TomlTable toml) {
    Long version = requireLong(toml, "version");
    if (version == null) {
      throw new SentinelException(
          "Missing required key 'version' in " + SentinelConfiguration.FILE_NAME);
    }
    if (version != SentinelConfiguration.SUPPORTED_VERSION) {
      throw new SentinelException(
          "Unsupported configuration version "
              + version
              + " (supported: "
              + SentinelConfiguration.SUPPORTED_VERSION
              + ")");
    }

    Map<String, GateConfiguration> gates = new LinkedHashMap<>();
    if (toml.contains(GATES_TABLE)) {
      TomlTable table = toml.getTable(GATES_TABLE);
      if (table == null) {
        throw new SentinelException("'" + GATES_TABLE + "' must be a table");
      }
      for (String name : table.keySet()) {
        TomlTable gate = table.getTable(List.of(name));
        if (gate == null) {
          throw new SentinelException("'" + GATES_TABLE + "." + name + "' must be a table");
        }
        gates.put(name, parseGate(name, gate));
      }
    }
    if (toml.contains("profiles"))
      throw new SentinelException(
          "'[profiles]' is no longer supported; declare 'profiles = [...]' under [quality-gates.<id>] instead");
    return new SentinelConfiguration((int) (long) version, gates);
  }

  private GateConfiguration parseGate(String name, TomlTable gate) {
    String where = GATES_TABLE + "." + name;
    Set<String> profiles = parseProfiles(where, gate);
    boolean enabled = true;
    if (gate.contains("enabled")) {
      Boolean value = gate.isBoolean("enabled") ? gate.getBoolean("enabled") : null;
      if (value == null) {
        throw new SentinelException("'" + where + ".enabled' must be true or false");
      }
      enabled = value;
    }
    if (!gate.contains("command")) {
      if (enabled) {
        throw new SentinelException("'" + where + ".command' is required for an enabled gate");
      }
      return new GateConfiguration(false, List.of(), profiles);
    }
    return new GateConfiguration(enabled, parseCommand(where, gate), profiles);
  }

  private Set<String> parseProfiles(String where, TomlTable gate) {
    if (!gate.contains("profiles")) return Set.of(SentinelConfiguration.DEFAULT_PROFILE);
    String key = where + ".profiles";
    if (!gate.isArray("profiles"))
      throw new SentinelException("'" + key + "' must be a non-empty array of non-blank strings");
    TomlArray array = gate.getArray("profiles");
    if (array == null || array.isEmpty())
      throw new SentinelException("'" + key + "' must not be empty");
    Set<String> profiles = new LinkedHashSet<>();
    for (int i = 0; i < array.size(); i++) {
      if (!array.isString(i) || array.getString(i) == null || array.getString(i).isBlank())
        throw new SentinelException("'" + key + "' must contain only non-blank strings");
      profiles.add(array.getString(i).trim());
    }
    return profiles;
  }

  private List<String> parseCommand(String where, TomlTable gate) {
    List<String> command;
    if (gate.isString("command")) {
      String value = gate.getString("command");
      if (value == null) {
        throw new SentinelException("'" + where + ".command' must be a string");
      }
      command = CommandLineTokenizer.tokenize(value);
    } else if (gate.isArray("command")) {
      TomlArray array = gate.getArray("command");
      if (array == null) {
        throw new SentinelException("'" + where + ".command' must be an array of strings");
      }
      command = new ArrayList<>();
      for (int i = 0; i < array.size(); i++) {
        if (!array.isString(i)) {
          throw new SentinelException("'" + where + ".command' array must contain only strings");
        }
        command.add(array.getString(i));
      }
    } else {
      throw new SentinelException(
          "'" + where + ".command' must be a string or an array of strings");
    }
    if (command.isEmpty() || command.getFirst().isBlank()) {
      throw new SentinelException("'" + where + ".command' must not be empty");
    }
    return command;
  }

  private Long requireLong(TomlTable toml, String key) {
    if (!toml.contains(key)) {
      return null;
    }
    if (!toml.isLong(key)) {
      throw new SentinelException("'" + key + "' must be an integer");
    }
    return toml.getLong(key);
  }
}
