package dev.ward.domain.init;

import java.util.List;

/** A missing gate executable and either its resolved install command or manual guidance. */
public record PlannedGateInstallation(
    String tool, String manager, List<String> command, String guidance) {
  /** Copies the command. */
  public PlannedGateInstallation {
    command = List.copyOf(command);
  }

  /** Returns whether a package manager can install the executable automatically. */
  public boolean installable() {
    return !command.isEmpty();
  }
}
