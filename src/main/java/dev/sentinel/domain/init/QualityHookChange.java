package dev.sentinel.domain.init;

import dev.sentinel.domain.agent.IntegrationResult;
import java.nio.file.Path;
import java.util.List;

/** Snapshot and result for one quality-hook installation transaction. */
public record QualityHookChange(List<HookSnapshot> snapshots, IntegrationResult result) {
  public QualityHookChange {
    snapshots = List.copyOf(snapshots);
  }

  public record HookSnapshot(Path path, boolean existed, String content, boolean executable) {}
}
