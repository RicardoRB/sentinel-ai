package dev.sentinel.domain.init;

import dev.sentinel.domain.agent.PlannedFileChange;
import java.util.List;
import java.util.Optional;

/** Everything initialization would change, computed without touching the project. */
public record InitPlan(
    List<PlannedFileChange> files,
    Optional<PlannedInstallation> installation,
    List<PlannedGateInstallation> gateInstallations) {
  public InitPlan(
      final List<PlannedFileChange> files, final Optional<PlannedInstallation> installation) {
    this(files, installation, List.of());
  }

  public InitPlan {
    files = List.copyOf(files);
    installation = installation == null ? Optional.empty() : installation;
    gateInstallations = gateInstallations == null ? List.of() : List.copyOf(gateInstallations);
  }

  /** Whether a file in the plan would stop initialization. */
  public boolean hasConflict() {
    return files.stream().anyMatch(file -> file.action() == PlannedFileChange.Action.CONFLICT);
  }

  /** The entries worth showing to a user: every file except those left unchanged. */
  public List<PlannedFileChange> visibleFiles() {
    return files.stream()
        .filter(file -> file.action() != PlannedFileChange.Action.UNCHANGED)
        .toList();
  }
}
