package dev.sentinel.domain.config;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record GateConfiguration(boolean enabled, List<String> command, Set<String> profiles) {

  public GateConfiguration {
    command = List.copyOf(command);
    profiles = Collections.unmodifiableSet(new LinkedHashSet<>(profiles));
  }

  public GateConfiguration(boolean enabled, List<String> command) {
    this(enabled, command, Set.of(SentinelConfiguration.DEFAULT_PROFILE));
  }
}
