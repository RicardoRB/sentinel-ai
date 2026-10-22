package dev.ward.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.agent.GuardMode;
import dev.ward.domain.agent.IntegrationCapability;
import dev.ward.domain.agent.IntegrationHealth;
import dev.ward.domain.agent.IntegrationRequest;
import dev.ward.domain.agent.IntegrationResult;
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
        .contains("ward-owned: codex-native-hooks v1");
    assertThat(Files.readString(root.resolve(".codex/hooks/ward.sh")))
        .contains("ward hook native --agent codex");

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
    assertThat(root.resolve(".codex/hooks/ward.sh")).doesNotExist();
    assertThat(integration.health(root).status()).isEqualTo(IntegrationHealth.Status.CONFLICT);
  }

  @Test
  void reportsPartialInstallation(@TempDir final Path root) throws Exception {
    final CodexIntegration integration = new CodexIntegration();
    integration.integrate(root, IntegrationRequest.INSTALL);
    Files.delete(root.resolve(".codex/hooks/ward.sh"));

    assertThat(integration.health(root).status()).isEqualTo(IntegrationHealth.Status.PARTIAL);
  }

  @Test
  void refreshWithoutAGuardOptionKeepsTheInstalledGuardState(@TempDir final Path root)
      throws Exception {
    final Path hook = root.resolve(".amp/plugins/ward.sh");
    final AmpIntegration integration = new AmpIntegration();

    integration.integrate(root, new IntegrationRequest(false, GuardMode.ENABLE));
    assertThat(Files.readString(hook)).contains("[ \"enable\" = \"enable\" ]");
    assertThat(integration.integrate(root, IntegrationRequest.INSTALL).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
    assertThat(Files.readString(hook)).contains("[ \"enable\" = \"enable\" ]");

    integration.integrate(root, new IntegrationRequest(false, GuardMode.DISABLE));
    assertThat(Files.readString(hook)).doesNotContain("[ \"enable\" = \"enable\" ]");
    integration.integrate(root, IntegrationRequest.INSTALL);
    assertThat(Files.readString(hook)).doesNotContain("[ \"enable\" = \"enable\" ]");
  }
}
