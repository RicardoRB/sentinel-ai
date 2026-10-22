package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.GuardMode;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The two Cline hook files. Cline runs every file named after an event with {@code bash} and has no
 * matcher, so each script filters the tool name itself before calling Ward.
 */
final class ClineHookScripts {
  private ClineHookScripts() {}

  /** The PostToolUse hook: the quality check after editor and apply_patch calls. */
  static String postToolUse(final String marker) {
    return script(
        marker,
        "PostToolUse",
        onlyTools("editor", "apply_patch")
            + "printf '%s' \"$payload\" | exec ward hook native --agent cline --event-stdin\n");
  }

  /** The PreToolUse hook: the approved-artifact guard for edits, patches and commands. */
  static String preToolUse(final String marker, final GuardMode guardMode) {
    return script(
        marker,
        "PreToolUse",
        "[ \""
            + guardMode.name().toLowerCase(Locale.ROOT)
            + "\" = \"enable\" ] || exit 0\n"
            + onlyTools("editor", "apply_patch", "run_commands")
            + "printf '%s' \"$payload\" | exec ward hook native --agent cline"
            + " --guard-approved-specs --event-stdin\n");
  }

  /** Lets the hook continue only for the named tools. */
  private static String onlyTools(final String... tools) {
    return Arrays.stream(tools)
        .map(tool -> "*'\"toolName\":\"" + tool + "\"'*")
        .collect(
            Collectors.joining("|", "case \"$payload\" in\n  ", ") ;;\n  *) exit 0 ;;\nesac\n"));
  }

  private static String script(final String marker, final String event, final String body) {
    return "#!/bin/sh\n# "
        + marker
        + "\n# Cline "
        + event
        + " hook. Ward always checks the project this file is in.\n"
        + "payload=$(cat)\n"
        + "cd \"$(dirname \"$0\")/../..\" || exit 0\n"
        + body;
  }
}
