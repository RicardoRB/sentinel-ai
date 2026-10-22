package dev.ward.infrastructure.config;

import dev.ward.domain.config.GateConfiguration;
import dev.ward.domain.config.WardConfiguration;
import dev.ward.domain.config.WardConfigurationReader;
import dev.ward.domain.config.WardException;
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

/** Reads {@code ward.toml} into the configuration model. */
public class TomlConfigurationReader implements WardConfigurationReader {

  private static final String GATES_TABLE = "quality-gates";

  /** Creates the stateless adapter that Dagger injects. */
  @Inject
  public TomlConfigurationReader() {}

  @Override
  public WardConfiguration read(final Path file) {
    if (!Files.isRegularFile(file)) {
      throw new WardException("Configuration file not found: " + file + ". Run 'ward init' first.");
    }
    final TomlParseResult toml;
    try {
      toml = Toml.parse(file);
    } catch (IOException e) {
      throw new WardException("Could not read " + file + ": " + e.getMessage(), e);
    }
    if (toml.hasErrors()) {
      throw new WardException("Invalid TOML in " + file + ": " + toml.errors().getFirst());
    }
    return parse(toml);
  }

  WardConfiguration parse(final TomlTable toml) {
    final Long version = requireLong(toml, "version");
    if (version == null) {
      throw new WardException("Missing required key 'version' in " + WardConfiguration.FILE_NAME);
    }
    if (version != WardConfiguration.SUPPORTED_VERSION) {
      throw new WardException(
          "Unsupported configuration version "
              + version
              + " (supported: "
              + WardConfiguration.SUPPORTED_VERSION
              + ")");
    }
    final Map<String, GateConfiguration> gates = parseGates(toml);
    if (toml.contains("profiles")) {
      throw new WardException(
          "'[profiles]' is no longer supported; declare 'profiles = [...]' under [quality-gates.<id>] instead");
    }
    return new WardConfiguration((int) (long) version, gates, parsePreset(toml));
  }

  private static Map<String, GateConfiguration> parseGates(final TomlTable toml) {
    final Map<String, GateConfiguration> gates = new LinkedHashMap<>();
    if (!toml.contains(GATES_TABLE)) {
      return gates;
    }
    final TomlTable table = toml.getTable(GATES_TABLE);
    if (table == null) {
      throw new WardException("'" + GATES_TABLE + "' must be a table");
    }
    for (final String name : table.keySet()) {
      final TomlTable gate = table.getTable(List.of(name));
      if (gate == null) {
        throw new WardException("'" + GATES_TABLE + "." + name + "' must be a table");
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
      throw new WardException("'preset' must be one of: standard, strict");
    }
    return preset;
  }

  private Long requireLong(final TomlTable toml, final String key) {
    if (!toml.contains(key)) {
      return null;
    }
    if (!toml.isLong(key)) {
      throw new WardException("'" + key + "' must be an integer");
    }
    return toml.getLong(key);
  }
}
