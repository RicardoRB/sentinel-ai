package dev.sentinel.domain.init;

import java.util.Optional;

/** Port for opening a terminal session that reads individual key bytes. */
@FunctionalInterface
public interface RawTerminal {
  /** Opens a raw-key session, or returns empty when the input is not a terminal. */
  Optional<RawSession> open();

  /** An open raw-key session; closing it restores the terminal mode. */
  interface RawSession extends AutoCloseable {
    /** Reads one key byte, or -1 at end of input. */
    int read();

    @Override
    void close();
  }
}
