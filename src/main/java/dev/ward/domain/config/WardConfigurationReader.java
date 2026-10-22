package dev.ward.domain.config;

import java.nio.file.Path;

/** Port for loading the configuration file. */
@FunctionalInterface
public interface WardConfigurationReader {

  /**
   * @throws WardException if the file is missing, unparseable or invalid
   */
  WardConfiguration read(Path file);
}
