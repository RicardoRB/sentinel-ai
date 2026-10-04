package dev.sentinel.infrastructure;

import dev.sentinel.domain.config.CommandLineTokenizer;
import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.config.SentinelConfigurationReader;
import dev.sentinel.domain.config.SentinelException;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TomlConfigurationReader implements SentinelConfigurationReader {

    private static final String GATES_TABLE = "quality-gates";

    @Override
    public SentinelConfiguration read(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new SentinelException("Configuration file not found: " + file + ". Run 'sentinel init' first.");
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
            throw new SentinelException("Missing required key 'version' in " + SentinelConfiguration.FILE_NAME);
        }
        if (version != SentinelConfiguration.SUPPORTED_VERSION) {
            throw new SentinelException("Unsupported configuration version " + version
                    + " (supported: " + SentinelConfiguration.SUPPORTED_VERSION + ")");
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
        return new SentinelConfiguration((int) (long) version, gates);
    }

    private GateConfiguration parseGate(String name, TomlTable gate) {
        String where = GATES_TABLE + "." + name;
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
            return new GateConfiguration(false, List.of());
        }
        return new GateConfiguration(enabled, parseCommand(where, gate));
    }

    private List<String> parseCommand(String where, TomlTable gate) {
        List<String> command;
        if (gate.isString("command")) {
            command = CommandLineTokenizer.tokenize(gate.getString("command"));
        } else if (gate.isArray("command")) {
            TomlArray array = gate.getArray("command");
            command = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                if (!array.isString(i)) {
                    throw new SentinelException("'" + where + ".command' array must contain only strings");
                }
                command.add(array.getString(i));
            }
        } else {
            throw new SentinelException("'" + where + ".command' must be a string or an array of strings");
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
