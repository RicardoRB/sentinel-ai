package dev.sentinel.domain.init;

import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.agent.PlannedFileChange;
import java.nio.file.Path;
import java.util.List;

/** Port for ownership-safe local quality-hook installation. */
public interface QualityHookGeneration {
  /** Installs the Sentinel quality hooks, keeping user-owned hook files untouched. */
  QualityHookChange apply(Path projectRoot);

  /** Removes the Sentinel-owned quality hooks and reports what was removed. */
  IntegrationResult remove(Path projectRoot);

  /** Describes, without writing, what {@link #apply} would do with each hook file. */
  List<PlannedFileChange> plan(Path projectRoot);

  /** Restores the hook files to their state before {@code change}. */
  void rollback(QualityHookChange change);
}
