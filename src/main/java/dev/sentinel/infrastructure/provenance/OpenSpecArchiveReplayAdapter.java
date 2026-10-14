package dev.sentinel.infrastructure.provenance;

import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.process.CommandResult;
import dev.sentinel.domain.provenance.ArchiveReplay;
import dev.sentinel.domain.provenance.GitRevisionSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;
import javax.inject.Inject;

/** Replays a pinned OpenSpec archive in a temporary tree and never mutates the source checkout. */
public final class OpenSpecArchiveReplayAdapter implements ArchiveReplay {
  private static final String OPENSPEC_VERSION = "1.14.1";
  private final CommandExecutor executor;

  @Inject
  public OpenSpecArchiveReplayAdapter(final CommandExecutor executor) {
    this.executor = executor;
  }

  @Override
  public GitRevisionSnapshot replay(
      final Path repository, final GitRevisionSnapshot baseline, final String archivePath) {
    try {
      final Path staging = Files.createTempDirectory("sentinel-openspec-replay-");
      try {
        for (final Map.Entry<String, String> entry : baseline.files().entrySet()) {
          final Path target = staging.resolve(entry.getKey());
          Files.createDirectories(Objects.requireNonNull(target.toAbsolutePath().getParent()));
          Files.writeString(target, entry.getValue());
        }
        final String archiveName = archivePath.substring(archivePath.lastIndexOf('/') + 1);
        final Path sourceArchive = repository.resolve(archivePath);
        if (Files.isDirectory(sourceArchive)) {
          copy(sourceArchive, staging.resolve(archivePath));
        }
        final CommandResult result =
            executor.execute(List.of("openspec", "archive", archiveName, "--yes"), staging);
        if (!result.succeeded()) {
          throw new IllegalStateException(
              "OpenSpec " + OPENSPEC_VERSION + " archive replay failed: " + result.stderr().trim());
        }
        return snapshot(staging, archivePath);
      } finally {
        delete(staging);
      }
    } catch (IOException e) {
      throw new IllegalStateException("Could not create isolated OpenSpec replay tree", e);
    }
  }

  private static GitRevisionSnapshot snapshot(final Path root, final String revision)
      throws IOException {
    final Map<String, String> files = new TreeMap<>();
    final Path openspec = root.resolve("openspec");
    if (Files.isDirectory(openspec)) {
      try (Stream<Path> paths = Files.walk(openspec)) {
        for (final Path path : paths.filter(Files::isRegularFile).toList()) {
          files.put(root.relativize(path).toString(), Files.readString(path));
        }
      }
    }
    return new GitRevisionSnapshot(revision, files);
  }

  private static void copy(final Path source, final Path target) throws IOException {
    try (Stream<Path> paths = Files.walk(source)) {
      for (final Path path : paths.toList()) {
        final Path destination = target.resolve(source.relativize(path));
        if (Files.isDirectory(path)) {
          Files.createDirectories(destination);
        } else {
          Files.copy(path, destination);
        }
      }
    }
  }

  private static void delete(final Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(root)) {
      for (final Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(path);
      }
    }
  }
}
