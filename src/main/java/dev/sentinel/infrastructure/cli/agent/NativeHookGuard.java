package dev.sentinel.infrastructure.cli.agent;

import dev.sentinel.application.spec.ApprovedArtifactGuardService;
import dev.sentinel.domain.agent.NativeHookInput;
import dev.sentinel.domain.agent.NativeHookOutcome;
import dev.sentinel.domain.spec.GuardRequest;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Checks a native hook event against the approved-artifact guard. */
final class NativeHookGuard {
  private NativeHookGuard() {}

  /** Returns the deny outcome for a refused tool call, or empty when the call is allowed. */
  static Optional<NativeHookOutcome> evaluate(
      final ApprovedArtifactGuardService guardService,
      final NativeHookInput event,
      final Path root) {
    return request(event, root)
        .map(guardService::evaluate)
        .filter(decision -> !decision.allowed())
        .map(decision -> NativeHookOutcome.deny(NativeHookMessages.bounded(decision.reason())));
  }

  static Optional<GuardRequest> request(final NativeHookInput event, final Path root) {
    final String workingDirectory = event.workingDirectory();
    if (event.unreadable()) {
      return Optional.of(GuardRequest.unreadable(root, "tool payload"));
    }
    if (event.path() != null) {
      return Optional.of(GuardRequest.paths(root, workingDirectory, List.of(event.path())));
    }
    if (event.patch() != null) {
      return Optional.of(GuardRequest.patch(root, workingDirectory, event.patch()));
    }
    if (present(event.command())) {
      return Optional.of(GuardRequest.shell(root, workingDirectory, event.command()));
    }
    if (present(event.skill())) {
      return Optional.of(GuardRequest.skill(root, event.skill()));
    }
    final String tool = event.toolName();
    return tool == null ? Optional.empty() : Optional.of(GuardRequest.unreadable(root, tool));
  }

  private static boolean present(final String value) {
    return value != null && !value.isBlank();
  }
}
