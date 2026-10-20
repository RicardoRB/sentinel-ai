package dev.sentinel.domain.architecturefixture;

import dev.sentinel.infrastructure.config.TomlConfigurationReader;

/**
 * Architecture fixture that the boundary rules must reject: it references an infrastructure
 * adapter.
 */
public final class FullyQualifiedAdapterReference {
  public TomlConfigurationReader adapter;
}
