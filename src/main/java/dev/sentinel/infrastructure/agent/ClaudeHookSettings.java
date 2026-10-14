package dev.sentinel.infrastructure.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Operations for preserving unrelated hook entries while adding or removing Sentinel hooks. */
final class ClaudeHookSettings {
  private static final List<String> MANAGED_EVENTS = List.of("PostToolUse", "Stop", "PreToolUse");

  private ClaudeHookSettings() {}

  static void merge(final Map<String, Object> existing, final Map<String, Object> desired) {
    for (final String event : MANAGED_EVENTS) {
      final Object configuredEntries = existing.get(event);
      final List<Object> preserved = withoutSentinelEntries(entries(configuredEntries, event));
      appendDesiredEntries(preserved, desired.get(event));
      if (preserved.isEmpty()) {
        existing.remove(event);
      } else {
        existing.put(event, preserved);
      }
    }
  }

  static void remove(final Map<String, Object> hooks) {
    final List<String> eventNames = new ArrayList<>(hooks.keySet());
    for (final String event : eventNames) {
      final Object value = hooks.get(event);
      if (!(value instanceof List<?> eventEntries)) {
        rejectInvalidManagedEvent(event);
        continue;
      }
      final List<Object> preserved = withoutSentinelEntries(eventEntries);
      if (preserved.isEmpty()) {
        hooks.remove(event);
      } else {
        hooks.put(event, preserved);
      }
    }
  }

  private static List<?> entries(final Object value, final String event) {
    if (value == null) {
      return List.of();
    }
    if (value instanceof List<?> list) {
      return list;
    }
    throw conflict(
        "Claude Code settings hook event '"
            + event
            + "' must be a JSON array; existing settings were preserved.");
  }

  private static void rejectInvalidManagedEvent(final String event) {
    if (MANAGED_EVENTS.contains(event)) {
      throw conflict(
          "Claude Code settings hook event '"
              + event
              + "' must be a JSON array; existing settings were preserved.");
    }
  }

  private static List<Object> withoutSentinelEntries(final List<?> entries) {
    final List<Object> preserved = new ArrayList<>();
    for (final Object entry : entries) {
      final Map<String, Object> mappedEntry = ClaudeSettingsDocument.object(entry);
      final Object commands = mappedEntry.get(ClaudeSettingsDocument.HOOKS_KEY);
      if (commands == null) {
        preserved.add(entry);
      } else {
        preserveNonSentinelCommands(preserved, entry, mappedEntry, commands);
      }
    }
    return preserved;
  }

  private static void preserveNonSentinelCommands(
      final List<Object> preserved,
      final Object originalEntry,
      final Map<String, Object> mappedEntry,
      final Object commands) {
    if (!(commands instanceof List<?> list)) {
      throw conflict(
          "Claude Code hook entries must contain a JSON array; existing settings were preserved.");
    }
    final List<Object> remaining =
        new ArrayList<>(list.stream().filter(item -> !SentinelClaudeHooks.isHook(item)).toList());
    if (remaining.size() == list.size()) {
      preserved.add(originalEntry);
    } else if (!remaining.isEmpty()) {
      mappedEntry.put(ClaudeSettingsDocument.HOOKS_KEY, remaining);
      preserved.add(mappedEntry);
    }
  }

  private static void appendDesiredEntries(
      final List<Object> preserved, final Object desiredEntries) {
    if (desiredEntries instanceof List<?> list) {
      list.stream()
          .filter(
              entry -> SentinelClaudeHooks.hasRegistration(ClaudeSettingsDocument.object(entry)))
          .forEach(preserved::add);
    }
  }

  private static ClaudeSettingsDocument.SettingsConflictException conflict(final String message) {
    return new ClaudeSettingsDocument.SettingsConflictException(message);
  }
}
