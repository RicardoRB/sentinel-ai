package dev.ward.domain.loop;

import dev.ward.domain.gate.CheckReport;
import java.util.List;

/**
 * How a quality loop ended.
 *
 * @param state why it stopped
 * @param iterations how many agent attempts ran
 * @param history the check report of each attempt
 * @param message a summary for the user
 */
public record LoopResult(
    LoopTerminalState state, int iterations, List<CheckReport> history, String message) {
  /** Copies the history. */
  public LoopResult {
    history = List.copyOf(history);
  }
}
