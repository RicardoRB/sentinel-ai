package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.ConfigurationStorage;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import javax.inject.Inject;

/** Filesystem implementation of initialization configuration persistence. */
public final class FileConfigurationStorage implements ConfigurationStorage {
  @Inject
  public FileConfigurationStorage() {}

  @Override
  public boolean exists(final Path file) {
    return Files.exists(file);
  }

  @Override
  public Optional<String> read(final Path file) {
    if (!Files.exists(file)) {
      return Optional.empty();
    }
    try {
      return Optional.of(Files.readString(file));
    } catch (IOException e) {
      throw new SentinelException("Could not read existing " + file + ": " + e.getMessage(), e);
    }
  }

  @Override
  public boolean create(final Path file, final String content) {
    try {
      Files.writeString(file, content, StandardOpenOption.CREATE_NEW);
      return true;
    } catch (FileAlreadyExistsException e) {
      return false;
    } catch (IOException e) {
      throw new SentinelException("Could not write " + file + ": " + e.getMessage(), e);
    }
  }

  @Override
  public void replace(final Path file, final String content) {
    write(file, content, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
  }

  @Override
  public void restore(final Path file, final String originalContent) {
    replace(file, originalContent);
  }

  private static void write(
      final Path file, final String content, final StandardOpenOption... options) {
    try {
      Files.writeString(file, content, options);
    } catch (IOException e) {
      throw new SentinelException("Could not write " + file + ": " + e.getMessage(), e);
    }
  }
}
