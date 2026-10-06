package dev.sentinel.domain.init;

import java.util.Optional;

/** Port for opening a terminal session that reads individual key bytes. */
public interface RawTerminal {
  Optional<RawSession> open();

  interface RawSession extends AutoCloseable {
    int read();

    @Override
    void close();
  }
}
