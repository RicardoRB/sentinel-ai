package dev.ward.domain.agent;

/**
 * One task given to an agent.
 *
 * @param task what the agent should do
 * @param timeoutSeconds how long the agent may run
 */
public record AgentRequest(String task, int timeoutSeconds) {
  /**
   * Validates the request.
   *
   * @throws IllegalArgumentException if the task is blank or the timeout is not positive
   */
  public AgentRequest {
    if (task == null || task.isBlank()) {
      throw new IllegalArgumentException("task must not be blank");
    }
    if (timeoutSeconds <= 0) {
      throw new IllegalArgumentException("timeoutSeconds must be positive");
    }
  }
}
