package dev.sentinel.infrastructure.update;

import dev.sentinel.domain.update.CachedCheck;
import dev.sentinel.domain.update.ReleaseVersion;
import dev.sentinel.domain.update.UpdateCheckCache;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Stores the last update lookup in the user's cache directory, never inside a project. */
public final class FileUpdateCheckCache implements UpdateCheckCache {
  private static final Logger LOGGER = Logger.getLogger(FileUpdateCheckCache.class.getName());
  private static final String CHECKED_AT = "checkedAt";
  private static final String LATEST = "latest";
  private static final String FILE = "update-check";

  private final Path file;

  /** Creates a cache stored at {@code file}. */
  public FileUpdateCheckCache(final Path file) {
    this.file = file;
  }

  /**
   * Returns the cache under {@code $XDG_CACHE_HOME/sentinel}, or {@code ~/.cache/sentinel} when
   * {@code XDG_CACHE_HOME} is unset; {@code environment} reads environment variables.
   */
  public static FileUpdateCheckCache forUser(
      final UnaryOperator<String> environment, final String userHome) {
    final String xdg = environment.apply("XDG_CACHE_HOME");
    final Path base = xdg == null || xdg.isBlank() ? Path.of(userHome, ".cache") : Path.of(xdg);
    return new FileUpdateCheckCache(base.resolve("sentinel").resolve(FILE));
  }

  @Override
  public Optional<CachedCheck> read() {
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try {
      final Map<String, String> values = new HashMap<>();
      for (final String line : Files.readAllLines(file)) {
        final int separator = line.indexOf('=');
        if (separator > 0) {
          values.put(line.substring(0, separator), line.substring(separator + 1));
        }
      }
      final Instant checkedAt = Instant.parse(values.getOrDefault(CHECKED_AT, ""));
      return ReleaseVersion.parse(values.get(LATEST))
          .map(latest -> new CachedCheck(checkedAt, latest));
    } catch (IOException | DateTimeParseException e) {
      LOGGER.log(Level.FINE, "event=update-cache status=unreadable");
      return Optional.empty();
    }
  }

  @Override
  public void write(final CachedCheck check) {
    final Path temporary = file.resolveSibling(FILE + "." + System.nanoTime() + ".tmp");
    try {
      final Path directory = file.toAbsolutePath().getParent();
      if (directory != null) {
        Files.createDirectories(directory);
      }
      Files.writeString(
          temporary,
          CHECKED_AT + "=" + check.checkedAt() + "\n" + LATEST + "=" + check.latest() + "\n");
      try {
        Files.move(
            temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (IOException e) {
      LOGGER.log(Level.FINE, "event=update-cache status=write-failed");
    } finally {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
        // The cache is a convenience; a leftover temporary file is harmless.
      }
    }
  }
}
