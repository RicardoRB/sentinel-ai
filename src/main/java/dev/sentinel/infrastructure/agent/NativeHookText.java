package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.NativeHookInput;
import dev.sentinel.domain.json.JsonCodec;
import java.util.Set;
import java.util.function.Function;

/** Text and payload helpers shared by the native harness adapters. */
final class NativeHookText {
  private NativeHookText() {}

  /** Returns the ownership marker written into an adapter's generated files. */
  static String marker(final String id) {
    return "sentinel-owned: " + id + "-native-hooks v1";
  }

  /** Returns {@code value} as a JSON string literal. */
  static String quote(final String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
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
