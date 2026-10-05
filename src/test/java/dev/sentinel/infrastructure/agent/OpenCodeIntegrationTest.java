package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.IntegrationResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCodeIntegrationTest {
    @Test
    void integratesAndRemovesOnlyOwnedContent() throws Exception {
        Path root = Files.createTempDirectory("sentinel-opencode");
        OpenCodeIntegration integration = new OpenCodeIntegration();
        IntegrationResult created = integration.integrate(root, false);
        assertThat(created.status()).isEqualTo(IntegrationResult.Status.CHANGED);
        assertThat(Files.exists(root.resolve(".opencode/commands/sentinel-check.md"))).isTrue();
        assertThat(Files.readString(root.resolve(".opencode/plugins/sentinel-edit-write.js")))
                .contains(OpenCodeIntegration.PLUGIN_MARKER, "tool.execute.after", "verify-quality.sh", "multiedit", "patch");
        assertThat(integration.integrate(root, false).status()).isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
        assertThat(integration.integrate(root, true).status()).isEqualTo(IntegrationResult.Status.REMOVED);
        assertThat(root.resolve(".opencode/plugins/sentinel-edit-write.js")).doesNotExist();
        assertThat(root.resolve(".opencode/commands/sentinel-check.md")).doesNotExist();
    }

    @Test
    void refusesToOverwriteUnownedCommand() throws Exception {
        Path target = Files.createTempDirectory("sentinel-opencode")
                .resolve(".opencode/commands/sentinel-check.md");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "user-owned");
        assertThat(new OpenCodeIntegration().integrate(target.getParent().getParent().getParent(), false).status())
                .isEqualTo(IntegrationResult.Status.CONFLICT);
    }

    @Test
    void refusesToInstallPluginWhenItConflictsWithoutTouchingCommand() throws Exception {
        Path root = Files.createTempDirectory("sentinel-opencode-plugin-conflict");
        Path plugin = root.resolve(".opencode/plugins/sentinel-edit-write.js");
        Files.createDirectories(plugin.getParent());
        Files.writeString(plugin, "user-owned");

        IntegrationResult result = new OpenCodeIntegration().integrate(root, false);
        assertThat(result.status()).isEqualTo(IntegrationResult.Status.CONFLICT);
        assertThat(root.resolve(".opencode/commands/sentinel-check.md")).doesNotExist();
        assertThat(plugin).hasContent("user-owned");
    }
}
