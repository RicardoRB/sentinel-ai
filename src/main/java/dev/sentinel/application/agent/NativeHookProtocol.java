package dev.sentinel.application.agent;

import dev.sentinel.domain.agent.NativeHookInput;
import dev.sentinel.domain.agent.NativeHookOutcome;
import dev.sentinel.domain.json.JsonCodec;
import java.util.Optional;

/** Application boundary for harness-specific native hook translation. */
public interface NativeHookProtocol {
  NativeHookInput parse(JsonCodec codec, String payload);

  boolean turnEnds(NativeHookInput input);

  String serialize(NativeHookOutcome outcome);

  /**
   * Returns the stdout envelope that delivers post-edit findings to the agent, for harnesses that
   * only pass tool feedback through hook output. Empty means the findings go to stderr with a
   * failing exit status.
   */
  default Optional<String> postEditFeedback(final String findings) {
    return Optional.empty();
  }

  /**
   * True for harnesses that refuse a tool call only on exit status 2, showing the agent stderr; the
   * denial is then written to stderr instead of being serialized to stdout.
   */
  default boolean deniesWithExitStatus() {
    return false;
  }
}
