package dev.ward.domain.update;

import java.util.Optional;

/** Port that remembers the last automatic update lookup for the current user. */
public interface UpdateCheckCache {
  /** Returns the last lookup, or empty when none is stored or it cannot be read. */
  Optional<CachedCheck> read();

  /** Stores {@code check}; a failure to store it is ignored. */
  void write(CachedCheck check);
}
