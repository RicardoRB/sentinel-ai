package dev.ward.infrastructure.gate;

import dev.ward.domain.gate.ProjectFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/** Reads project files from the local file system. */
public final class FileSystemProjectFiles implements ProjectFiles {
  private static final Logger LOGGER = Logger.getLogger(FileSystemProjectFiles.class.getName());

  /** Creates the adapter. */
  @Inject
  public FileSystemProjectFiles() {}

  @Override
  public boolean exists(final Path root, final String path) {
    return Files.isRegularFile(root.resolve(path));
  }

  @Override
  public boolean contains(final Path root, final String path, final String text) {
    try {
      return Files.readString(root.resolve(path), StandardCharsets.UTF_8).contains(text);
    } catch (IOException exception) {
      LOGGER.log(
          Level.WARNING,
          () -> "event=project-file-read-failed error=" + exception.getClass().getSimpleName());
      return false;
    }
  }
}
