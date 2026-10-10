package dev.sentinel.domain.config;

import java.nio.file.Path;

/** Port for loading the configuration file. */
@FunctionalInterface
public interface SentinelConfigurationReader {

  /**
   * @throws SentinelException if the file is missing, unparseable or invalid
   */
  SentinelConfiguration read(Path file);
}
