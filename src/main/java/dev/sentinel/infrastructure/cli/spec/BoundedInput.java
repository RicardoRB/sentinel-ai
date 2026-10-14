package dev.sentinel.infrastructure.cli.spec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Reads standard input as UTF-8 up to a fixed size. */
final class BoundedInput {
  /** The largest input accepted, which also covers whole-file writes in agent payloads. */
  static final int MAX_BYTES = 16 * 1024 * 1024;

  private BoundedInput() {}

  /** Returns the text, or empty when it cannot be read or exceeds {@code maxBytes}. */
  static Optional<String> read(final InputStream input, final int maxBytes) {
    try {
      final byte[] bytes = input.readNBytes(maxBytes + 1);
      return bytes.length > maxBytes
          ? Optional.empty()
          : Optional.of(new String(bytes, StandardCharsets.UTF_8));
    } catch (IOException e) {
      return Optional.empty();
    }
  }
}
