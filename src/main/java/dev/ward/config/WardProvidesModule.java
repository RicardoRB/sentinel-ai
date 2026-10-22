package dev.ward.config;

import dagger.Module;
import dagger.Provides;
import dev.ward.domain.agent.AgentIntegration;
import dev.ward.domain.config.PathExistence;
import dev.ward.domain.update.LatestReleaseLookup;
import dev.ward.domain.update.UpdateCheckCache;
import dev.ward.infrastructure.agent.AmpIntegration;
import dev.ward.infrastructure.agent.ClaudeCodeIntegration;
import dev.ward.infrastructure.agent.ClineIntegration;
import dev.ward.infrastructure.agent.CodexIntegration;
import dev.ward.infrastructure.agent.CopilotIntegration;
import dev.ward.infrastructure.agent.CursorIntegration;
import dev.ward.infrastructure.agent.FactoryDroidIntegration;
import dev.ward.infrastructure.agent.GeminiIntegration;
import dev.ward.infrastructure.agent.OpenCodeIntegration;
import dev.ward.infrastructure.agent.WindsurfIntegration;
import dev.ward.infrastructure.cli.VersionProvider;
import dev.ward.infrastructure.config.FilePathExistence;
import dev.ward.infrastructure.update.FileUpdateCheckCache;
import dev.ward.infrastructure.update.GithubLatestReleaseLookup;
import java.util.Set;
import javax.inject.Named;

@Module
final class WardProvidesModule {
  private WardProvidesModule() {}

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
  @Named("ward.version")
  static String version() {
    return VersionProvider.current();
  }

  @Provides
  static PathExistence pathExistence() {
    return new FilePathExistence();
  }

  @Provides
  static LatestReleaseLookup latestReleaseLookup() {
    return new GithubLatestReleaseLookup();
  }

  @Provides
  static UpdateCheckCache updateCheckCache() {
    return FileUpdateCheckCache.forUser(System::getenv, System.getProperty("user.home"));
  }
}
