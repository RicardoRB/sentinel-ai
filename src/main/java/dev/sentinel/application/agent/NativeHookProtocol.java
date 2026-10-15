package dev.sentinel.application.agent;

import dev.sentinel.domain.agent.NativeHookInput;
import dev.sentinel.domain.agent.NativeHookOutcome;
import dev.sentinel.domain.json.JsonCodec;

/** Application boundary for harness-specific native hook translation. */
public interface NativeHookProtocol {
  NativeHookInput parse(JsonCodec codec, String payload);

  boolean turnEnds(NativeHookInput input);

  String serialize(NativeHookOutcome outcome);
}
