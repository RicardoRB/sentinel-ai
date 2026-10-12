package dev.sentinel.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.agent.IntegrationRequest;
import dev.sentinel.domain.agent.IntegrationRequest.Guidance;
import dev.sentinel.domain.agent.IntegrationResult;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OpenCodeIntegrationTest {
  private static final IntegrationRequest INSTALL = IntegrationRequest.install(Guidance.EXCLUDED);
  private static final IntegrationRequest REMOVE = IntegrationRequest.remove(Guidance.EXCLUDED);

  @Test
  void integratesAndRemovesOnlyOwnedContent() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-opencode");
    final OpenCodeIntegration integration = new OpenCodeIntegration();
    final IntegrationResult created = integration.integrate(root, INSTALL);
    assertThat(created.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(Files.exists(root.resolve(".opencode/commands/sentinel-check.md"))).isTrue();
    assertThat(Files.readString(root.resolve(".opencode/plugins/sentinel-edit-write.js")))
        .contains(
            OpenCodeIntegration.PLUGIN_MARKER,
            "tool.execute.after",
            "sentinel",
            "--learn-after",
            "multiedit",
            "patch");
    assertThat(integration.integrate(root, INSTALL).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
    assertThat(integration.integrate(root, REMOVE).status())
        .isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(root.resolve(".opencode/plugins/sentinel-edit-write.js")).doesNotExist();
    assertThat(root.resolve(".opencode/commands/sentinel-check.md")).doesNotExist();
  }

  @Test
  void refusesToOverwriteUnownedCommand() throws Exception {
    final Path target =
        Files.createTempDirectory("sentinel-opencode")
            .resolve(".opencode/commands/sentinel-check.md");
    Files.createDirectories(target.getParent());
    Files.writeString(target, "user-owned");
    assertThat(
            new OpenCodeIntegration()
                .integrate(target.getParent().getParent().getParent(), INSTALL)
                .status())
        .isEqualTo(IntegrationResult.Status.CONFLICT);
  }

  @Test
  void refusesToInstallPluginWhenItConflictsWithoutTouchingCommand() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-opencode-plugin-conflict");
    final Path plugin = root.resolve(".opencode/plugins/sentinel-edit-write.js");
    Files.createDirectories(plugin.getParent());
    Files.writeString(plugin, "user-owned");

    final IntegrationResult result = new OpenCodeIntegration().integrate(root, INSTALL);
    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CONFLICT);
    assertThat(root.resolve(".opencode/commands/sentinel-check.md")).doesNotExist();
    assertThat(plugin).hasContent("user-owned");
  }
}
