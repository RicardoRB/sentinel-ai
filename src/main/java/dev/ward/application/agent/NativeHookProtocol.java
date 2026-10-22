package dev.ward.application.agent;

import dev.ward.domain.agent.NativeHookInput;
import dev.ward.domain.agent.NativeHookOutcome;
import dev.ward.domain.json.JsonCodec;
import java.util.Optional;

/** Application boundary for harness-specific native hook translation. */
public interface NativeHookProtocol {
  /** Extracts the harness-neutral event from a hook {@code payload}. */
  NativeHookInput parse(JsonCodec codec, String payload);

  /** Returns whether {@code input} is the harness's turn-end event. */
  boolean turnEnds(NativeHookInput input);

  /** Serializes {@code outcome} in the harness's response format. */
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
