package dev.sentinel.infrastructure.agent;

import java.util.List;
import java.util.Map;

/** Recognizes only Claude hook entries that point to Sentinel's dedicated hook scripts. */
final class SentinelClaudeHooks {
  private static final String EDIT_WRITE_COMMAND = ".claude/hooks/sentinel-edit-write";
  private static final String GUARD_COMMAND = ".claude/hooks/sentinel-openspec-guard";

  private SentinelClaudeHooks() {}

  static boolean hasRegistration(final Map<String, Object> eventEntry) {
    final Object configured = eventEntry.get(ClaudeSettingsDocument.HOOKS_KEY);
    return configured instanceof List<?> list
        && list.stream().anyMatch(SentinelClaudeHooks::isHook);
  }

  static boolean isHook(final Object value) {
    if (!(value instanceof Map<?, ?> hook)) {
      return false;
    }
    final Object command = hook.get("command");
    return EDIT_WRITE_COMMAND.equals(command) || GUARD_COMMAND.equals(command);
  }
}
