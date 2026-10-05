package dev.sentinel.domain.process;

import java.nio.file.Path;
import java.util.List;

/** Immutable process request. Arguments are passed directly to the operating system. */
public record Command(List<String> arguments, Path workingDirectory) {
  public Command {
    arguments = List.copyOf(arguments);
    if (arguments.isEmpty() || arguments.getFirst().isBlank()) {
      throw new IllegalArgumentException("A command must contain an executable");
    }
    workingDirectory = workingDirectory.toAbsolutePath().normalize();
  }
}
