package dev.ward.infrastructure.agent;

import dev.ward.domain.json.JsonCodec;
import dev.ward.domain.json.JsonCodecException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses and safely merges Ward hook registrations in a shared Claude Code settings file. */
final class ClaudeSettingsDocument {
  private static final String OWNERSHIP_KEY = "wardOwnership";
  static final String HOOKS_KEY = "hooks";

  private final JsonCodec codec;

  ClaudeSettingsDocument(final JsonCodec codec) {
    this.codec = codec;
  }

  String merge(
      final Path settings,
      final boolean guarded,
      final String ownershipMarker,
      final String regularSettings,
      final String guardedSettings)
      throws IOException {
    final Map<String, Object> root = readRoot(settings);
    validateOwnership(root, ownershipMarker);
    final Map<String, Object> hooks = readHooks(root);
    if (root.get(OWNERSHIP_KEY) == null && hasWardRegistration(hooks)) {
      throw new SettingsConflictException(
          "Claude Code settings contain a Ward hook path without Ward ownership; resolve the conflict explicitly.");
    }
    root.put(OWNERSHIP_KEY, ownershipMarker);
    final Map<String, Object> desired =
        object(codec.fromJson(guarded ? guardedSettings : regularSettings, Object.class));
    ClaudeHookSettings.merge(hooks, object(desired.get(HOOKS_KEY)));
    root.put(HOOKS_KEY, hooks);
    return codec.toPrettyJson(root) + System.lineSeparator();
  }

  SettingsUpdate remove(final Path settings, final String ownershipMarker) throws IOException {
    if (!Files.exists(settings)) {
      return null;
    }
    final Map<String, Object> root = readRoot(settings);
    final Object ownership = root.get(OWNERSHIP_KEY);
    validateOwnership(root, ownershipMarker);
    final Object configuredHooks = root.get(HOOKS_KEY);
    final Map<String, Object> hooks = readHooks(root);
    final boolean hasRegistrations = hasWardRegistration(hooks);
    if (hasRegistrations && ownership == null) {
      throw new SettingsConflictException(
          "Claude Code settings contain a Ward hook path without Ward ownership; resolve the conflict explicitly.");
    }
    ClaudeHookSettings.remove(hooks);
    if (configuredHooks != null) {
      root.put(HOOKS_KEY, hooks);
    }
    final boolean owned = ownershipMarker.equals(ownership);
    if (owned) {
      root.remove(OWNERSHIP_KEY);
    }
    if (!hasRegistrations && !owned) {
      return new SettingsUpdate(Files.readString(settings), false);
    }
    return new SettingsUpdate(codec.toPrettyJson(root) + System.lineSeparator(), true);
  }

  private Map<String, Object> readRoot(final Path settings) throws IOException {
    if (!Files.exists(settings)) {
      return new LinkedHashMap<>();
    }
    try {
      return object(codec.fromJson(Files.readString(settings), Object.class));
    } catch (JsonCodecException | IllegalArgumentException e) {
      throw new SettingsConflictException("Existing Claude Code settings are invalid JSON.", e);
    }
  }

  private static void validateOwnership(
      final Map<String, Object> root, final String ownershipMarker) {
    final Object ownership = root.get(OWNERSHIP_KEY);
    if (ownership != null && !ownershipMarker.equals(ownership)) {
      throw new SettingsConflictException(
          "Existing Claude Code settings use the Ward ownership key; resolve the conflict explicitly.");
    }
  }

  private static Map<String, Object> readHooks(final Map<String, Object> root) {
    final Object configuredHooks = root.get(HOOKS_KEY);
    if (configuredHooks == null) {
      return new LinkedHashMap<>();
    }
    if (!(configuredHooks instanceof Map<?, ?>)) {
      throw new SettingsConflictException(
          "Claude Code settings 'hooks' must be a JSON object; existing settings were preserved.");
    }
    return object(configuredHooks);
  }

  static Map<String, Object> object(final Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      throw new SettingsConflictException(
          "Claude Code settings must contain JSON objects at the expected paths.");
    }
    final Map<String, Object> copy = new LinkedHashMap<>();
    for (final Map.Entry<?, ?> entry : map.entrySet()) {
      if (!(entry.getKey() instanceof String key)) {
        throw new SettingsConflictException(
            "Claude Code settings contain a non-string JSON object key.");
      }
      copy.put(key, entry.getValue());
    }
    return copy;
  }

  private static boolean hasWardRegistration(final Map<String, Object> hooks) {
    for (final Object event : hooks.values()) {
      if (event instanceof List<?> entries) {
        for (final Object entry : entries) {
          if (WardClaudeHooks.hasRegistration(object(entry))) {
            return true;
          }
        }
      }
    }
    return false;
  }

  record SettingsUpdate(String content, boolean changed) {}

  static final class SettingsConflictException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    SettingsConflictException(final String message) {
      super(message);
    }

    SettingsConflictException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}
