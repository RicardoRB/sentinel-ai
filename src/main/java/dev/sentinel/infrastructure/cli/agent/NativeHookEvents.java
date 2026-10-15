package dev.sentinel.infrastructure.cli.agent;

import dev.sentinel.application.agent.NativeHookProtocol;
import dev.sentinel.domain.agent.NativeHookInput;
import dev.sentinel.domain.json.JsonCodec;
import dev.sentinel.domain.json.JsonCodecException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Reads a bounded native hook payload; anything unreadable becomes an unreadable event. */
final class NativeHookEvents {
  private static final int MAX_PAYLOAD_BYTES = 1_048_576;

  private NativeHookEvents() {}

  static NativeHookInput read(
      final InputStream input, final NativeHookProtocol protocol, final JsonCodec codec) {
    try {
      final byte[] bytes = input.readNBytes(MAX_PAYLOAD_BYTES + 1);
      if (bytes.length > MAX_PAYLOAD_BYTES) {
        return NativeHookInput.unreadableInput();
      }
      final String payload = new String(bytes, StandardCharsets.UTF_8);
      if (payload.isBlank()) {
        return NativeHookInput.unreadableInput();
      }
      return protocol.parse(codec, payload);
    } catch (IOException | JsonCodecException exception) {
      return NativeHookInput.unreadableInput();
    }
  }
}
