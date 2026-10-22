package dev.ward.domain.init;

import dev.ward.domain.agent.IntegrationResult;
import java.nio.file.Path;
import java.util.List;

/** Snapshot and result for one quality-hook installation transaction. */
public record QualityHookChange(List<HookSnapshot> snapshots, IntegrationResult result) {
  /** Copies the snapshots. */
  public QualityHookChange {
    snapshots = List.copyOf(snapshots);
  }

  /**
   * The state of one hook file before the change, used to restore it.
   *
   * @param path the hook file
   * @param existed whether the file existed
   * @param content its previous content, when it existed
   * @param executable whether it was executable
   */
  public record HookSnapshot(Path path, boolean existed, String content, boolean executable) {}
}
