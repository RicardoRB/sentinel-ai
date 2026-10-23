package dev.ward.infrastructure.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Operations for preserving unrelated hook entries while adding or removing Ward hooks. */
final class ClaudeHookSettings {
  private static final List<String> MANAGED_EVENTS = List.of("PostToolUse", "Stop", "PreToolUse");

  private ClaudeHookSettings() {}

  /** Replaces the hooks running {@code owned} commands with {@code desired}, keeping all others. */
  static void merge(
      final Map<String, Object> existing,
      final Map<String, Object> desired,
      final Set<String> owned) {
    for (final String event : MANAGED_EVENTS) {
      final List<?> current = entries(existing.get(event), event);
      final List<Object> preserved = withoutWardEntries(current, owned);
      insertDesiredEntries(preserved, desired.get(event), position(current, owned));
      if (preserved.isEmpty()) {
        existing.remove(event);
      } else {
        existing.put(event, preserved);
      }
    }
  }

  /** Removes the hooks running {@code owned} commands, keeping all others. */
  static void remove(final Map<String, Object> hooks, final Set<String> owned) {
    final List<String> eventNames = new ArrayList<>(hooks.keySet());
    for (final String event : eventNames) {
      final Object value = hooks.get(event);
      if (!(value instanceof List<?> eventEntries)) {
        rejectInvalidManagedEvent(event);
        continue;
      }
      final List<Object> preserved = withoutWardEntries(eventEntries, owned);
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

  private static List<Object> withoutWardEntries(final List<?> entries, final Set<String> owned) {
    final List<Object> preserved = new ArrayList<>();
    for (final Object entry : entries) {
      final Map<String, Object> mappedEntry = ClaudeSettingsDocument.object(entry);
      final Object commands = mappedEntry.get(ClaudeSettingsDocument.HOOKS_KEY);
      if (commands == null) {
        preserved.add(entry);
      } else {
        preserveNonWardCommands(preserved, entry, mappedEntry, commands, owned);
      }
    }
    return preserved;
  }

  private static void preserveNonWardCommands(
      final List<Object> preserved,
      final Object originalEntry,
      final Map<String, Object> mappedEntry,
      final Object commands,
      final Set<String> owned) {
    if (!(commands instanceof List<?> list)) {
      throw conflict(
          "Claude Code hook entries must contain a JSON array; existing settings were preserved.");
    }
    final List<Object> remaining =
        new ArrayList<>(
            list.stream().filter(item -> !WardClaudeHooks.isHook(item, owned)).toList());
    if (remaining.size() == list.size()) {
      preserved.add(originalEntry);
    } else if (!remaining.isEmpty()) {
      mappedEntry.put(ClaudeSettingsDocument.HOOKS_KEY, remaining);
      preserved.add(mappedEntry);
    }
  }

  /**
   * Where this project's entries go: where they were, so re-installing one project does not move it
   * behind another project's entries; at the end when it had none.
   */
  private static int position(final List<?> current, final Set<String> owned) {
    for (int i = 0; i < current.size(); i++) {
      if (WardClaudeHooks.hasRegistration(ClaudeSettingsDocument.object(current.get(i)), owned)) {
        return withoutWardEntries(current.subList(0, i), owned).size();
      }
    }
    return -1;
  }

  private static void insertDesiredEntries(
      final List<Object> preserved, final Object desiredEntries, final int position) {
    if (desiredEntries instanceof List<?> list) {
      final List<Object> desired =
          list.stream()
              .filter(
                  entry -> WardClaudeHooks.hasRegistration(ClaudeSettingsDocument.object(entry)))
              .map(Object.class::cast)
              .toList();
      preserved.addAll(position < 0 ? preserved.size() : position, desired);
    }
  }

  private static ClaudeSettingsDocument.SettingsConflictException conflict(final String message) {
    return new ClaudeSettingsDocument.SettingsConflictException(message);
  }
}
