package dev.sentinel.config;

import dagger.Module;
import dagger.Provides;
import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.IntegrationCapability;
import dev.sentinel.infrastructure.agent.ClaudeCodeIntegration;
import dev.sentinel.infrastructure.agent.NativeAgentIntegration;
import dev.sentinel.infrastructure.agent.OpenCodeIntegration;
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
        nativeIntegration(
            "codex",
            ".codex/hooks.json",
            ".codex/hooks/sentinel.sh",
            "PreToolUse,PostToolUse",
            true),
        nativeIntegration(
            "cursor",
            ".cursor/hooks.json",
            ".cursor/hooks/sentinel.sh",
            "preToolUse,afterFileEdit,stop",
            true),
        nativeIntegration(
            "copilot",
            ".github/hooks/sentinel.json",
            ".github/hooks/sentinel.sh",
            "preToolUse,postToolUse,sessionEnd",
            true),
        nativeIntegration(
            "gemini",
            ".gemini/settings.json",
            ".gemini/hooks/sentinel.sh",
            "BeforeTool,AfterTool,AfterAgent,SessionEnd",
            true),
        nativeIntegration(
            "windsurf",
            ".windsurf/hooks.json",
            ".windsurf/hooks/sentinel.sh",
            "post_write_code,post_run_command,post_cascade_response",
            false),
        nativeIntegration(
            "cline",
            ".cline/hooks/sentinel.json",
            ".cline/hooks/sentinel.sh",
            "beforeTool,afterTool,turn_end",
            true),
        nativeIntegration(
            "factory-droid",
            ".factory/plugins/sentinel.json",
            ".factory/plugins/sentinel.sh",
            "PostToolUse,Stop,SessionEnd",
            false),
        nativeIntegration(
            "amp",
            ".amp/plugins/sentinel.ts",
            ".amp/plugins/sentinel.sh",
            "tool.call,tool.result,agent.end",
            true));
  }

  private static NativeAgentIntegration nativeIntegration(
      final String id,
      final String config,
      final String hook,
      final String events,
      final boolean preTool) {
    final Set<IntegrationCapability> capabilities =
        preTool
            ? Set.of(
                IntegrationCapability.PRE_TOOL,
                IntegrationCapability.POST_EDIT,
                IntegrationCapability.TURN_END,
                IntegrationCapability.FEEDBACK,
                IntegrationCapability.APPROVED_ARTIFACT_GUARD)
            : Set.of(IntegrationCapability.POST_EDIT, IntegrationCapability.FEEDBACK);
    return new NativeAgentIntegration(
        new NativeAgentIntegration.Definition(id, config, hook, events, capabilities));
  }

  @Provides
  @Named("sentinel.version")
  static String version() {
    final String version = SentinelProvidesModule.class.getPackage().getImplementationVersion();
    return version == null || version.isBlank() ? "dev" : version;
  }
}
