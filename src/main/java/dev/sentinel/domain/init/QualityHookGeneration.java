package dev.sentinel.domain.init;

import dev.sentinel.domain.agent.IntegrationResult;
import java.nio.file.Path;

/** Port for ownership-safe local quality-hook installation. */
public interface QualityHookGeneration {
  QualityHookChange apply(Path projectRoot);

  IntegrationResult remove(Path projectRoot);

  void rollback(QualityHookChange change);
}
