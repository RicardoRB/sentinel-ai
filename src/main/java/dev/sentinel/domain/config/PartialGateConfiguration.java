package dev.sentinel.domain.config;

import java.util.List;
import java.util.Objects;

/** Configuration describing how a quality gate can be scoped to edited files. */
public record PartialGateConfiguration(
    List<String> inputs, List<String> config, List<String> args, String item, String separator) {

  public PartialGateConfiguration {
    inputs = List.copyOf(inputs);
    config = List.copyOf(config);
    args = List.copyOf(args);
    item = Objects.requireNonNullElse(item, "{path}");
  }
}
