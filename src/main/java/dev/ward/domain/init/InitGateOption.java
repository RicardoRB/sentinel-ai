package dev.ward.domain.init;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A user-selectable quality gate and the command/environment facts presented by init.
 *
 * @param id the gate ID
 * @param description what the gate checks
 * @param command the argument vector init writes
 * @param available whether the gate's tool is available
 * @param availabilityMessage why the tool is or is not available
 * @param settings extra string keys init writes in the gate's table, in order
 */
public record InitGateOption(
    String id,
    String description,
    List<String> command,
    boolean available,
    String availabilityMessage,
    Map<String, String> settings) {
  /** Copies the command and settings. */
  public InitGateOption {
    command = List.copyOf(command);
    settings = Collections.unmodifiableMap(new LinkedHashMap<>(settings));
  }

  /** Creates an option without extra settings. */
  public InitGateOption(
      final String id,
      final String description,
      final List<String> command,
      final boolean available,
      final String availabilityMessage) {
    this(id, description, command, available, availabilityMessage, Map.of());
  }

  /** Creates an option whose description is its {@code id}. */
  public InitGateOption(
      final String id,
      final List<String> command,
      final boolean available,
      final String availabilityMessage) {
    this(id, id, command, available, availabilityMessage);
  }
}
