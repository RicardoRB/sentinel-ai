package dev.sentinel.config;

import dagger.Module;
import dagger.Provides;
import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.config.PathExistence;
import dev.sentinel.infrastructure.agent.AmpIntegration;
import dev.sentinel.infrastructure.agent.ClaudeCodeIntegration;
import dev.sentinel.infrastructure.agent.ClineIntegration;
import dev.sentinel.infrastructure.agent.CodexIntegration;
import dev.sentinel.infrastructure.agent.CopilotIntegration;
import dev.sentinel.infrastructure.agent.CursorIntegration;
import dev.sentinel.infrastructure.agent.FactoryDroidIntegration;
import dev.sentinel.infrastructure.agent.GeminiIntegration;
import dev.sentinel.infrastructure.agent.OpenCodeIntegration;
import dev.sentinel.infrastructure.agent.WindsurfIntegration;
import dev.sentinel.infrastructure.cli.VersionProvider;
import dev.sentinel.infrastructure.config.FilePathExistence;
import java.util.Set;
import javax.inject.Named;

@Module
final class SentinelProvidesModule {
  private SentinelProvidesModule() {}

  @Provides
  static Set<AgentIntegration> agentIntegrations() {
    return Set.of(
        new OpenCodeIntegration(),
        new ClaudeCodeIntegration(),
        new CodexIntegration(),
        new CursorIntegration(),
        new CopilotIntegration(),
        new GeminiIntegration(),
        new WindsurfIntegration(),
        new ClineIntegration(),
        new FactoryDroidIntegration(),
        new AmpIntegration());
  }

  @Provides
  @Named("sentinel.version")
  static String version() {
    return VersionProvider.current();
  }

  @Provides
  static PathExistence pathExistence() {
    return new FilePathExistence();
  }
}
