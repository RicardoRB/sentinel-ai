package dev.ward.domain.gate;

import dev.ward.domain.project.Project;
import java.time.Instant;
import java.util.List;

/**
 * Port that turns a gate result into structured findings.
 *
 * <p>Implementations read only artifacts written at or after {@code gateStart} and return an empty
 * list when a gate has no supported source. Unreadable reports raise {@link
 * FindingExtractionException}; callers must not let it change the gate verdict.
 */
@FunctionalInterface
public interface FindingExtractor {
  FindingExtractor NONE = (project, result, gateStart) -> List.of();

  /**
   * Returns the findings of {@code result}, read from artifacts written since {@code gateStart}.
   */
  List<Finding> extract(Project project, GateResult result, Instant gateStart);
}
