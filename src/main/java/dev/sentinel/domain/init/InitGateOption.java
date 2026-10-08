package dev.sentinel.domain.init;

import java.util.List;

/** A user-selectable quality gate and the command/environment facts presented by init. */
public record InitGateOption(
    String id,
    String description,
    List<String> command,
    boolean available,
    String availabilityMessage) {
  public InitGateOption {
    command = List.copyOf(command);
  }

  public InitGateOption(
      final String id,
      final List<String> command,
      final boolean available,
      final String availabilityMessage) {
    this(id, id, command, available, availabilityMessage);
  }
}
