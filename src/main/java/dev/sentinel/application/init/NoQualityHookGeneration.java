package dev.sentinel.application.init;

import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.agent.PlannedFileChange;
import dev.sentinel.domain.init.QualityHookChange;
import dev.sentinel.domain.init.QualityHookGeneration;
import java.nio.file.Path;
import java.util.List;

/** No-op quality-hook adapter retained for lightweight application tests and callers. */
final class NoQualityHookGeneration implements QualityHookGeneration {
  @Override
  public QualityHookChange apply(final Path projectRoot) {
    throw new IllegalStateException("Quality hooks are unavailable in this runtime.");
  }

  @Override
  public IntegrationResult remove(final Path projectRoot) {
    throw new IllegalStateException("Quality hooks are unavailable in this runtime.");
  }

  @Override
  public List<PlannedFileChange> plan(final Path projectRoot) {
    throw new IllegalStateException("Quality hooks are unavailable in this runtime.");
  }

  @Override
  public void rollback(final QualityHookChange change) {}
}
