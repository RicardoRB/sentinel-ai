package dev.sentinel.application;

import dev.sentinel.domain.agent.IntegrationResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeCodeIntegrationTest {
    @Test
    void installsIdempotentlyAndRemovesOwnedArtifacts() throws Exception {
        Path root = Files.createTempDirectory("sentinel-claude");
        ClaudeCodeIntegration integration = new ClaudeCodeIntegration();

        IntegrationResult created = integration.integrate(root, false);
        assertThat(created.status()).isEqualTo(IntegrationResult.Status.CHANGED);
        Path settings = root.resolve(".claude/settings.json");
        Path hook = root.resolve(".claude/hooks/sentinel-edit-write");
        assertThat(settings).hasContent(Files.readString(settings));
        assertThat(Files.readString(settings)).contains("PostToolUse", "^(Edit|Write|MultiEdit)$", ClaudeCodeIntegration.SETTINGS_MARKER);
        assertThat(Files.readString(hook)).contains(ClaudeCodeIntegration.HOOK_MARKER, "./verify-quality.sh");
        assertThat(integration.integrate(root, false).status()).isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
        assertThat(integration.integrate(root, true).status()).isEqualTo(IntegrationResult.Status.REMOVED);
        assertThat(settings).doesNotExist();
        assertThat(hook).doesNotExist();
    }

    @Test
    void preservesConflictingUserOwnedSettings() throws Exception {
        Path root = Files.createTempDirectory("sentinel-claude-conflict");
        Path settings = root.resolve(".claude/settings.json");
        Files.createDirectories(settings.getParent());
        Files.writeString(settings, "{\"hooks\":{}}\n");

        IntegrationResult result = new ClaudeCodeIntegration().integrate(root, false);
        assertThat(result.status()).isEqualTo(IntegrationResult.Status.CONFLICT);
        assertThat(settings).hasContent("{\"hooks\":{}}\n");
        assertThat(root.resolve(".claude/hooks/sentinel-edit-write")).doesNotExist();
    }
}
