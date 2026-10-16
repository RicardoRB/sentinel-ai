package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.GuardMode;
import dev.sentinel.domain.agent.NativeHookInput;
import dev.sentinel.domain.json.JsonCodec;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/** Text and payload helpers shared by the native harness adapters. */
final class NativeHookText {
  /** Characters below this code point are JSON control characters and must be escaped. */
  private static final char FIRST_PRINTABLE = 0x20;

  private NativeHookText() {}

  /** Returns the ownership marker written into an adapter's generated files. */
  static String marker(final String id) {
    return "sentinel-owned: " + id + "-native-hooks v1";
  }

  /**
   * Returns {@code value} as a JSON string literal, escaping quotes, backslashes and control
   * characters.
   */
  static String quote(final String value) {
    final StringBuilder json = new StringBuilder(value.length() + 2).append('"');
    for (int i = 0; i < value.length(); i++) {
      final char ch = value.charAt(i);
      switch (ch) {
        case '"' -> json.append("\\\"");
        case '\\' -> json.append("\\\\");
        case '\n' -> json.append("\\n");
        case '\r' -> json.append("\\r");
        case '\t' -> json.append("\\t");
        default -> {
          if (ch < FIRST_PRINTABLE) {
            json.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
          } else {
            json.append(ch);
          }
        }
      }
    }
    return json.append('"').toString();
  }

  /**
   * Returns the phase-aware hook wrapper. Native registrations call it with {@code pre} for
   * pre-tool events, which run the approved-artifact guard only when it is enabled, and with {@code
   * post} for post-tool and turn-end events, which run the quality check.
   */
  static String phaseWrapper(final String id, final String hookFile, final GuardMode guardMode) {
    final String up = "/..".repeat((int) hookFile.chars().filter(ch -> ch == '/').count());
    return "#!/bin/sh\n# "
        + marker(id)
        + "\n# Native event adapter for "
        + id
        + ". Usage: sentinel.sh pre|post, with the event JSON on stdin.\n"
        + "# Hooks may run in a subdirectory, so Sentinel always checks the project this file is in.\n"
        + "cd \"$(dirname \"$0\")"
        + up
        + "\" || exit 2\n"
        + "case \"$1\" in\n"
        + "  pre)\n"
        + "    [ \""
        + guardMode.name().toLowerCase(Locale.ROOT)
        + "\" = \"enable\" ] || exit 0\n"
        + "    exec sentinel hook native --agent "
        + id
        + " --guard-approved-specs --event-stdin ;;\n"
        + "  post) exec sentinel hook native --agent "
        + id
        + " --event-stdin ;;\n"
        + "  *) echo \"Sentinel hook wrapper: expected pre or post.\" >&2; exit 2 ;;\n"
        + "esac\n";
  }

  /** Returns the generic {@code {"decision":"deny"}} response used by several harnesses. */
  static String genericDenial(final String reason) {
    return "{\"decision\":\"deny\",\"reason\":" + quote(reason) + "}";
  }

  /** Decodes a payload, treating JSON {@code null} as unreadable input. */
  static <T> NativeHookInput parse(
      final JsonCodec codec,
      final String payload,
      final Class<T> type,
      final Function<T, NativeHookInput> input) {
    final T value = codec.fromJson(payload, type);
    return value == null ? NativeHookInput.unreadableInput() : input.apply(value);
  }

  /** Returns whether the event has one of {@code names}; an event without a name never matches. */
  static boolean eventIn(final NativeHookInput input, final Set<String> names) {
    return input.eventName() != null && names.contains(input.eventName());
  }
}
