package dev.sentinel.infrastructure.agent;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recognizes only Claude hook entries that point to Sentinel's dedicated hook scripts, in their
 * current project-anchored form and the relative form written by earlier versions.
 */
final class SentinelClaudeHooks {
  private static final String PROJECT_DIR = "${CLAUDE_PROJECT_DIR}/";
  private static final Set<String> COMMANDS =
      Set.of(
          ".claude/hooks/sentinel-edit-write",
          ".claude/hooks/sentinel-openspec-guard",
          PROJECT_DIR + ".claude/hooks/sentinel-edit-write",
          PROJECT_DIR + ".claude/hooks/sentinel-openspec-guard");

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
    return command instanceof String text && COMMANDS.contains(text);
  }
}
