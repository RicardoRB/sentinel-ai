package dev.sentinel.infrastructure.config;

import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelConfigurationReader;
import dev.sentinel.domain.config.SentinelException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

public class TomlConfigurationReader implements SentinelConfigurationReader {

  private static final String GATES_TABLE = "quality-gates";

  @Inject
  public TomlConfigurationReader() {}

  @Override
  public SentinelConfiguration read(final Path file) {
    if (!Files.isRegularFile(file)) {
      throw new SentinelException(
          "Configuration file not found: " + file + ". Run 'sentinel init' first.");
    }
    final TomlParseResult toml;
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

  SentinelConfiguration parse(final TomlTable toml) {
    final Long version = requireLong(toml, "version");
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
    final Map<String, GateConfiguration> gates = parseGates(toml);
    if (toml.contains("profiles")) {
      throw new SentinelException(
          "'[profiles]' is no longer supported; declare 'profiles = [...]' under [quality-gates.<id>] instead");
    }
    return new SentinelConfiguration((int) (long) version, gates, parsePreset(toml));
  }

  private static Map<String, GateConfiguration> parseGates(final TomlTable toml) {
    final Map<String, GateConfiguration> gates = new LinkedHashMap<>();
    if (!toml.contains(GATES_TABLE)) {
      return gates;
    }
    final TomlTable table = toml.getTable(GATES_TABLE);
    if (table == null) {
      throw new SentinelException("'" + GATES_TABLE + "' must be a table");
    }
    for (final String name : table.keySet()) {
      final TomlTable gate = table.getTable(List.of(name));
      if (gate == null) {
        throw new SentinelException("'" + GATES_TABLE + "." + name + "' must be a table");
      }
      gates.put(name, TomlGateParser.parse(GATES_TABLE + "." + name, gate));
    }
    return gates;
  }

  private static String parsePreset(final TomlTable toml) {
    if (!toml.contains("preset")) {
      return null;
    }
    final String preset = toml.isString("preset") ? toml.getString("preset") : null;
    if (!"standard".equals(preset) && !"strict".equals(preset)) {
      throw new SentinelException("'preset' must be one of: standard, strict");
    }
    return preset;
  }

  private Long requireLong(final TomlTable toml, final String key) {
    if (!toml.contains(key)) {
      return null;
    }
    if (!toml.isLong(key)) {
      throw new SentinelException("'" + key + "' must be an integer");
    }
    return toml.getLong(key);
  }
}
