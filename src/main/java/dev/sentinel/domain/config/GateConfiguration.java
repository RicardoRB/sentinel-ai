package dev.sentinel.domain.config;

import java.util.List;

public record GateConfiguration(boolean enabled, List<String> command) {

  public GateConfiguration {
    command = List.copyOf(command);
  }
}
