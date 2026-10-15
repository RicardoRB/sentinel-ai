package dev.sentinel.infrastructure.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Captures and restores native integration targets when a multi-file operation fails. */
final class NativeIntegrationTransaction {
  private final List<FileSnapshot> snapshots;

  private NativeIntegrationTransaction(final List<FileSnapshot> snapshots) {
    this.snapshots = List.copyOf(snapshots);
  }

  static NativeIntegrationTransaction capture(final Path... paths) throws IOException {
    final List<FileSnapshot> snapshots = new ArrayList<>();
    for (final Path path : paths) {
      snapshots.add(new FileSnapshot(path, Files.exists(path) ? Files.readString(path) : null));
    }
    return new NativeIntegrationTransaction(snapshots);
  }

  void rollback() throws IOException {
    for (final FileSnapshot snapshot : snapshots) {
      if (snapshot.content() == null) {
        Files.deleteIfExists(snapshot.path());
      } else {
        Files.createDirectories(snapshot.path().getParent());
        Files.writeString(snapshot.path(), snapshot.content());
      }
    }
  }

  private record FileSnapshot(Path path, String content) {}
}
