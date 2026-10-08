package dev.sentinel.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.agent.IntegrationResult;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ClaudeCodeIntegrationTest {
  @Test
  void installsIdempotentlyAndRemovesOwnedArtifacts() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-claude");
    final ClaudeCodeIntegration integration = new ClaudeCodeIntegration();

    final IntegrationResult created = integration.integrate(root, false);
    assertThat(created.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    final Path settings = root.resolve(".claude/settings.json");
    final Path hook = root.resolve(".claude/hooks/sentinel-edit-write");
    assertThat(settings).hasContent(Files.readString(settings));
    assertThat(Files.readString(settings))
        .contains("PostToolUse", "^(Edit|Write|MultiEdit)$", ClaudeCodeIntegration.SETTINGS_MARKER);
    assertThat(Files.readString(hook))
        .contains(
            ClaudeCodeIntegration.HOOK_MARKER, "sentinel hook claude-code", "--learn-after 3");
    assertThat(integration.integrate(root, false).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
    assertThat(integration.integrate(root, true).status())
        .isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(settings).doesNotExist();
    assertThat(hook).doesNotExist();
  }

  @Test
  void preservesConflictingUserOwnedSettings() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-claude-conflict");
    final Path settings = root.resolve(".claude/settings.json");
    Files.createDirectories(settings.getParent());
    Files.writeString(settings, "{\"hooks\":{}}\n");

    final IntegrationResult result = new ClaudeCodeIntegration().integrate(root, false);
    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CONFLICT);
    assertThat(settings).hasContent("{\"hooks\":{}}\n");
    assertThat(root.resolve(".claude/hooks/sentinel-edit-write")).doesNotExist();
  }

  @Test
  void upgradesOutdatedOwnedHookAndReportsItChanged() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-claude-upgrade");
    final Path settings = root.resolve(".claude/settings.json");
    final Path hook = root.resolve(".claude/hooks/sentinel-edit-write");
    Files.createDirectories(hook.getParent());
    Files.writeString(settings, ClaudeCodeIntegration.SETTINGS_MARKER + "\nold settings\n");
    Files.writeString(
        hook,
        "#!/bin/sh\n"
            + "# "
            + ClaudeCodeIntegration.HOOK_MARKER
            + "\n"
            + "exec ./verify-quality.sh\n");

    final IntegrationResult result = new ClaudeCodeIntegration().integrate(root, false);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(result.changed()).contains(settings.toString(), hook.toString());
    assertThat(Files.readString(hook))
        .contains(ClaudeCodeIntegration.HOOK_MARKER, "sentinel hook claude-code", "--learn-after 3")
        .doesNotContain("./verify-quality.sh");
    assertThat(Files.readString(settings)).contains("PostToolUse");
    assertThat(new ClaudeCodeIntegration().integrate(root, false).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
  }
}
