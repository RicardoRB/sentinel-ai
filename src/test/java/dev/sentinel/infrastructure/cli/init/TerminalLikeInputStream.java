package dev.sentinel.infrastructure.cli.init;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Delivers input one line per read and never reports bytes as available, like a terminal where the
 * user has not typed the next answer yet.
 */
final class TerminalLikeInputStream extends InputStream {
  private static final int UNSIGNED_BYTE = 0xFF;
  private static final byte LINE_END = '\n';

  private final byte[] bytes;
  private int position;

  TerminalLikeInputStream(final String input) {
    super();
    this.bytes = input.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public int read() {
    if (position >= bytes.length) {
      return -1;
    }
    final int next = bytes[position] & UNSIGNED_BYTE;
    position++;
    return next;
  }

  @Override
  public int read(final byte[] target, final int offset, final int length) {
    if (position >= bytes.length) {
      return -1;
    }
    int count = 0;
    boolean lineEnded = false;
    while (count < length && position < bytes.length && !lineEnded) {
      final byte next = bytes[position];
      target[offset + count] = next;
      position++;
      count++;
      lineEnded = next == LINE_END;
    }
    return count;
  }

  @Override
  public int available() {
    return 0;
  }
}
