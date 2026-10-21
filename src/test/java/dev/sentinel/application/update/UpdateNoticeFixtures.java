package dev.sentinel.application.update;

import dev.sentinel.domain.update.CachedCheck;
import dev.sentinel.domain.update.ReleaseVersion;
import dev.sentinel.domain.update.UpdateCheckCache;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Builds update services over an in-memory cache and a counting fake lookup. */
final class UpdateNoticeFixtures {
  static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");
  static final ReleaseVersion NEWER = new ReleaseVersion(0, 4, 0);
  static final ReleaseVersion CURRENT = new ReleaseVersion(0, 3, 1);

  final AtomicInteger lookups = new AtomicInteger();
  final MemoryCache cache = new MemoryCache();

  UpdateNoticeService service(
      final String running,
      final Optional<ReleaseVersion> latest,
      final Map<String, String> env,
      final boolean windows) {
    return new UpdateNoticeService(
        running,
        () -> {
          lookups.incrementAndGet();
          return latest;
        },
        cache,
        new UpdateContext(InstantSource.fixed(NOW), env::get, windows));
  }

  UpdateNoticeService service(final Optional<ReleaseVersion> latest) {
    return service("0.3.1", latest, Map.of(), false);
  }

  /** A cache that keeps the last check in memory. */
  static final class MemoryCache implements UpdateCheckCache {
    Optional<CachedCheck> stored = Optional.empty();

    @Override
    public Optional<CachedCheck> read() {
      return stored;
    }

    @Override
    public void write(final CachedCheck check) {
      stored = Optional.of(check);
    }
  }
}
