package dev.sentinel.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.agent.IntegrationCapability;
import dev.sentinel.domain.agent.IntegrationHealth;
import dev.sentinel.domain.agent.IntegrationRequest;
import dev.sentinel.domain.agent.IntegrationResult;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeAgentIntegrationTest {
  @Test
  void installsRefreshesAndRemovesOwnedArtifacts(@TempDir final Path root) throws Exception {
    final CodexIntegration integration = new CodexIntegration();

    final IntegrationResult created = integration.integrate(root, IntegrationRequest.INSTALL);
    assertThat(created.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(created.capabilities()).contains(IntegrationCapability.PRE_TOOL);
    assertThat(Files.readString(root.resolve(".codex/hooks.json")))
        .contains("sentinel-owned: codex-native-hooks v1");
    assertThat(Files.readString(root.resolve(".codex/hooks/sentinel.sh")))
        .contains("sentinel hook native --agent codex");

    assertThat(integration.integrate(root, IntegrationRequest.INSTALL).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
    assertThat(integration.integrate(root, IntegrationRequest.REMOVE).status())
        .isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(integration.installed(root)).isFalse();
  }

  @Test
  void preservesUserOwnedTargetsAsConflicts(@TempDir final Path root) throws Exception {
    final Path config = root.resolve(".codex/hooks.json");
    Files.createDirectories(config.getParent());
    Files.writeString(config, "user configuration");

    final CodexIntegration integration = new CodexIntegration();
    final IntegrationResult result = integration.integrate(root, IntegrationRequest.INSTALL);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CONFLICT);
    assertThat(Files.readString(config)).isEqualTo("user configuration");
    assertThat(root.resolve(".codex/hooks/sentinel.sh")).doesNotExist();
    assertThat(integration.health(root).status()).isEqualTo(IntegrationHealth.Status.CONFLICT);
  }

  @Test
  void reportsPartialInstallation(@TempDir final Path root) throws Exception {
    final CodexIntegration integration = new CodexIntegration();
    integration.integrate(root, IntegrationRequest.INSTALL);
    Files.delete(root.resolve(".codex/hooks/sentinel.sh"));

    assertThat(integration.health(root).status()).isEqualTo(IntegrationHealth.Status.PARTIAL);
  }
}
