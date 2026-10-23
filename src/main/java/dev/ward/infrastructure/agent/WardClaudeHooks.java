package dev.ward.infrastructure.agent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Recognizes only Claude hook entries that point to Ward's dedicated hook scripts: a root project's
 * scripts in their current project-anchored form and the relative form written by earlier versions,
 * and the suffixed scripts of nested projects.
 */
final class WardClaudeHooks {
  private static final Set<String> ROOT_COMMANDS = new ClaudeCodeFiles("").commands();
  private static final Pattern NESTED_COMMAND =
      Pattern.compile(
          "\\$\\{CLAUDE_PROJECT_DIR}/\\.claude/hooks/ward-(edit-write|openspec-guard)-[a-z0-9-]+");
  private static final Predicate<String> ANY_PROJECT =
      command -> ROOT_COMMANDS.contains(command) || NESTED_COMMAND.matcher(command).matches();

  private WardClaudeHooks() {}

  /** Returns whether the event entry registers a hook of any Ward project. */
  static boolean hasRegistration(final Map<String, Object> eventEntry) {
    return hasRegistration(eventEntry, ANY_PROJECT);
  }

  /** Returns whether the event entry registers one of {@code commands}. */
  static boolean hasRegistration(final Map<String, Object> eventEntry, final Set<String> commands) {
    return hasRegistration(eventEntry, commands::contains);
  }

  /** Returns whether the hook is a hook of any Ward project. */
  static boolean isHook(final Object value) {
    return isHook(value, ANY_PROJECT);
  }

  /** Returns whether the hook runs one of {@code commands}. */
  static boolean isHook(final Object value, final Set<String> commands) {
    return isHook(value, commands::contains);
  }

  private static boolean hasRegistration(
      final Map<String, Object> eventEntry, final Predicate<String> commands) {
    final Object configured = eventEntry.get(ClaudeSettingsDocument.HOOKS_KEY);
    return configured instanceof List<?> list
        && list.stream().anyMatch(item -> isHook(item, commands));
  }

  private static boolean isHook(final Object value, final Predicate<String> commands) {
    if (!(value instanceof Map<?, ?> hook)) {
      return false;
    }
    final Object command = hook.get("command");
    return command instanceof String text && commands.test(text);
  }
}
