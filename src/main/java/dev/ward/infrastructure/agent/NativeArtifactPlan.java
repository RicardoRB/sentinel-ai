package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.IntegrationCapability;
import java.nio.file.Path;
import java.util.Set;

/** Adapter-provided content plan consumed by shared ownership-safe persistence. */
public record NativeArtifactPlan(
    String id,
    Path configPath,
    Path hookPath,
    String marker,
    String configContent,
    String hookContent,
    Set<IntegrationCapability> capabilities) {
  /** Copies the capabilities. */
  public NativeArtifactPlan {
    capabilities = Set.copyOf(capabilities);
  }
}
