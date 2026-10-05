package dev.sentinel.infrastructure.cli;

import java.nio.file.Path;
import picocli.CommandLine.Option;

/** Options shared by every command that works on a project. */
public class ProjectOptions {

  @Option(
      names = {"-C", "--directory"},
      paramLabel = "<dir>",
      description = "Run as if started in <dir> (default: current directory).")
  private Path directory;

  public Path directory() {
    return directory != null ? directory : Path.of("").toAbsolutePath();
  }
}
