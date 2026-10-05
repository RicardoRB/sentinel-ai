package dev.sentinel.domain.loop;

import java.nio.file.Path;
import java.util.List;

/** Runtime values for one bounded quality-loop run. */
public record LoopRequest(
    Path workingDirectory,
    List<String> agentCommand,
    String task,
    LoopConfiguration configuration) {
  public LoopRequest {
    if (workingDirectory == null)
      throw new IllegalArgumentException("workingDirectory is required");
    agentCommand = List.copyOf(agentCommand);
    if (agentCommand.isEmpty())
      throw new IllegalArgumentException("agentCommand must not be empty");
    if (task == null || task.isBlank())
      throw new IllegalArgumentException("task must not be blank");
    if (configuration == null) throw new IllegalArgumentException("configuration is required");
  }
}
