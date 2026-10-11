package dev.sentinel.application.init;

import dev.sentinel.domain.doctor.EnvironmentInspection;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.project.Project;
import java.util.List;

/** Shared construction helpers for project-aware gate choices. */
final class InitGateOptionFactory {
  private InitGateOptionFactory() {}

  static InitGateOption binaryOption(
      final EnvironmentInspection environment,
      final Project project,
      final String id,
      final String description,
      final List<String> command,
      final String hint) {
    final boolean available = environment.inspect(project.root()).hasExecutable(command.getFirst());
    final String message =
        available
            ? "Executable available: " + command.getFirst()
            : command.getFirst() + " is not on the path. " + hint;
    return new InitGateOption(id, description, command, available, message);
  }

  static InitGateOption option(
      final String id,
      final String description,
      final boolean available,
      final String message,
      final String... command) {
    return new InitGateOption(id, description, List.of(command), available, message);
  }
}
