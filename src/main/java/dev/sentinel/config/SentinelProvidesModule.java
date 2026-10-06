package dev.sentinel.config;

import dagger.Module;
import dagger.Provides;
import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.infrastructure.agent.ClaudeCodeIntegration;
import dev.sentinel.infrastructure.agent.OpenCodeIntegration;
import java.util.Set;
import javax.inject.Named;

@Module
final class SentinelProvidesModule {
  private SentinelProvidesModule() {}

  @Provides
  static Set<AgentIntegration> agentIntegrations() {
    return Set.of(new OpenCodeIntegration(), new ClaudeCodeIntegration());
  }

  @Provides
  @Named("sentinel.version")
  static String version() {
    String version = SentinelProvidesModule.class.getPackage().getImplementationVersion();
    return version == null || version.isBlank() ? "dev" : version;
  }
}
