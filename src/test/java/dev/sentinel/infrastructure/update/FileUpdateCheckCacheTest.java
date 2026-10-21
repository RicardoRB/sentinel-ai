package dev.sentinel.infrastructure.update;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.update.CachedCheck;
import dev.sentinel.domain.update.ReleaseVersion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileUpdateCheckCacheTest {
  private static final CachedCheck CHECK =
      new CachedCheck(Instant.parse("2026-10-21T10:00:00Z"), new ReleaseVersion(0, 4, 0));

  @TempDir Path directory;

  @Test
  void roundTripsTheLastCheck() {
    final FileUpdateCheckCache cache =
        new FileUpdateCheckCache(directory.resolve("sentinel/update-check"));

    assertThat(cache.read()).isEmpty();
    cache.write(CHECK);

    assertThat(cache.read()).contains(CHECK);
    try (Stream<Path> files = Files.list(directory.resolve("sentinel"))) {
      assertThat(files)
          .extracting(Path::getFileName)
          .map(Path::toString)
          .containsExactly("update-check");
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @Test
  void corruptFileReadsAsEmpty() throws IOException {
    final Path file = directory.resolve("update-check");
    Files.writeString(file, "checkedAt=yesterday\nlatest=0.4.0\n");
    assertThat(new FileUpdateCheckCache(file).read()).isEmpty();

    Files.writeString(file, "checkedAt=2026-10-21T10:00:00Z\nlatest=dev\n");
    assertThat(new FileUpdateCheckCache(file).read()).isEmpty();
  }

  @Test
  void unwritableLocationIsIgnored() throws IOException {
    final Path blocker = Files.writeString(directory.resolve("blocker"), "a file, not a directory");
    final FileUpdateCheckCache cache = new FileUpdateCheckCache(blocker.resolve("update-check"));

    cache.write(CHECK);

    assertThat(cache.read()).isEmpty();
  }

  @Test
  void resolvesTheUserCacheDirectory() {
    final Path xdg = directory.resolve("xdg");
    FileUpdateCheckCache.forUser(Map.of("XDG_CACHE_HOME", xdg.toString())::get, "/unused")
        .write(CHECK);
    assertThat(xdg.resolve("sentinel/update-check")).isRegularFile();

    FileUpdateCheckCache.forUser(name -> null, directory.toString()).write(CHECK);
    assertThat(directory.resolve(".cache/sentinel/update-check")).isRegularFile();
  }
}
