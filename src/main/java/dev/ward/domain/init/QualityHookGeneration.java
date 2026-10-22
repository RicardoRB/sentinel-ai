package dev.ward.domain.init;

import dev.ward.domain.agent.IntegrationResult;
import dev.ward.domain.agent.PlannedFileChange;
import java.nio.file.Path;
import java.util.List;

/** Port for ownership-safe local quality-hook installation. */
public interface QualityHookGeneration {
  /** Installs the Ward quality hooks, keeping user-owned hook files untouched. */
  QualityHookChange apply(Path projectRoot);

  /** Removes the Ward-owned quality hooks and reports what was removed. */
  IntegrationResult remove(Path projectRoot);

  /** Describes, without writing, what {@link #apply} would do with each hook file. */
  List<PlannedFileChange> plan(Path projectRoot);

  /** Restores the hook files to their state before {@code change}. */
  void rollback(QualityHookChange change);
}
