package dev.sentinel.domain.init;

import java.util.List;

/** A missing gate executable and either its resolved install command or manual guidance. */
public record PlannedGateInstallation(
    String tool, String manager, List<String> command, String guidance) {
  public PlannedGateInstallation {
    command = List.copyOf(command);
  }

  public boolean installable() {
    return !command.isEmpty();
  }
}
