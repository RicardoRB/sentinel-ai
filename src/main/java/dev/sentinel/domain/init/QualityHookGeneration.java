package dev.sentinel.domain.init;

import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.agent.PlannedFileChange;
import java.nio.file.Path;
import java.util.List;

/** Port for ownership-safe local quality-hook installation. */
public interface QualityHookGeneration {
  QualityHookChange apply(Path projectRoot);

  IntegrationResult remove(Path projectRoot);

  /** Describes, without writing, what {@link #apply} would do with each hook file. */
  List<PlannedFileChange> plan(Path projectRoot);

  void rollback(QualityHookChange change);
}
