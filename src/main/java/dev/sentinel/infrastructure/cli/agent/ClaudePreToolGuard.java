package dev.sentinel.infrastructure.cli.agent;

import dev.sentinel.domain.spec.GuardRequest;
import java.nio.file.Path;
import java.util.Optional;

/** Maps Claude Code {@code PreToolUse} payloads to approved-artifact guard requests. */
final class ClaudePreToolGuard {
  private ClaudePreToolGuard() {}

  /** Returns the guard request for a guarded tool, or empty for tools the guard ignores. */
  static Optional<GuardRequest> request(final Path start, final ClaudeHookEvent event) {
    final String tool = event.toolName == null ? "" : event.toolName;
    final ClaudeHookEvent.ToolInput input = event.input().orElseGet(ClaudeHookEvent.ToolInput::new);
    return switch (tool) {
      case "Edit", "Write", "MultiEdit", "NotebookEdit" ->
          Optional.of(GuardRequest.paths(start, event.cwd, input.writtenPaths(tool)));
      case "Bash" -> Optional.of(GuardRequest.shell(start, event.cwd, input.text(tool)));
      case "Skill" -> Optional.of(GuardRequest.skill(start, input.text(tool)));
      default -> Optional.empty();
    };
  }
}
