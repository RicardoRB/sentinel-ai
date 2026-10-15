package dev.sentinel.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.IntegrationCapability;
import dev.sentinel.domain.agent.IntegrationRequest;
import dev.sentinel.domain.agent.IntegrationResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NativeAgentAdapterContractTest {
  @ParameterizedTest
  @MethodSource("adapters")
  void eachHarnessHasAnExplicitContract(
      final AgentIntegration integration,
      final String config,
      final String hook,
      final boolean preTool,
      @TempDir final Path root)
      throws Exception {
    final IntegrationResult result = integration.integrate(root, IntegrationRequest.INSTALL);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(integration.getClass()).isNotEqualTo(NativeAgentIntegration.class);
    assertThat(root.resolve(config)).isRegularFile();
    assertThat(root.resolve(hook)).isRegularFile();
    assertThat(result.capabilities()).contains(IntegrationCapability.FEEDBACK);
    assertThat(result.capabilities().contains(IntegrationCapability.PRE_TOOL)).isEqualTo(preTool);
    assertThat(Files.readString(root.resolve(config))).contains(integration.id());
  }

  @Test
  void ampUsesItsTypeScriptPluginContract(@TempDir final Path root) throws Exception {
    final AmpIntegration integration = new AmpIntegration();

    integration.integrate(root, IntegrationRequest.INSTALL);

    assertThat(Files.readString(root.resolve(".amp/plugins/sentinel.ts")))
        .contains("export default function", "tool.call", "agent.end");
  }

  @ParameterizedTest
  @MethodSource("adapters")
  void eachHarnessRefreshesRemovesAndPreservesUserFiles(
      final AgentIntegration integration,
      final String config,
      final String hook,
      final boolean preTool,
      @TempDir final Path root)
      throws Exception {
    assertThat(integration.capabilities().contains(IntegrationCapability.PRE_TOOL))
        .isEqualTo(preTool);
    integration.integrate(root, IntegrationRequest.INSTALL);
    final Path configPath = root.resolve(config);
    final Path hookPath = root.resolve(hook);
    Files.writeString(configPath, Files.readString(configPath) + "\n// stale\n");

    assertThat(integration.integrate(root, IntegrationRequest.INSTALL).status())
        .isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(integration.integrate(root, IntegrationRequest.REMOVE).status())
        .isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(configPath).doesNotExist();
    assertThat(hookPath).doesNotExist();

    Files.createDirectories(configPath.getParent());
    Files.writeString(configPath, "user-owned configuration");
    assertThat(integration.integrate(root, IntegrationRequest.INSTALL).status())
        .isEqualTo(IntegrationResult.Status.CONFLICT);
    assertThat(Files.readString(configPath)).isEqualTo("user-owned configuration");
  }

  private static Stream<Arguments> adapters() {
    return Stream.of(
        Arguments.of(new CodexIntegration(), ".codex/hooks.json", ".codex/hooks/sentinel.sh", true),
        Arguments.of(
            new CursorIntegration(), ".cursor/hooks.json", ".cursor/hooks/sentinel.sh", true),
        Arguments.of(
            new CopilotIntegration(),
            ".github/hooks/sentinel.json",
            ".github/hooks/sentinel.sh",
            true),
        Arguments.of(
            new GeminiIntegration(), ".gemini/settings.json", ".gemini/hooks/sentinel.sh", true),
        Arguments.of(
            new WindsurfIntegration(),
            ".windsurf/hooks.json",
            ".windsurf/hooks/sentinel.sh",
            false),
        Arguments.of(
            new ClineIntegration(), ".cline/hooks/sentinel.json", ".cline/hooks/sentinel.sh", true),
        Arguments.of(
            new FactoryDroidIntegration(),
            ".factory/plugins/sentinel.json",
            ".factory/plugins/sentinel.sh",
            false),
        Arguments.of(
            new AmpIntegration(), ".amp/plugins/sentinel.ts", ".amp/plugins/sentinel.sh", true));
  }
}
