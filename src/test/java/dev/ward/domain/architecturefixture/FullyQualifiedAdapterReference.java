package dev.ward.domain.architecturefixture;

import dev.ward.infrastructure.config.TomlConfigurationReader;

/**
 * Architecture fixture that the boundary rules must reject: it references an infrastructure
 * adapter.
 */
public final class FullyQualifiedAdapterReference {
  public TomlConfigurationReader adapter;
}
