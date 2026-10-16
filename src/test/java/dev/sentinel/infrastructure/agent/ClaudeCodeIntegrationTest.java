package dev.sentinel.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.JsonTree;
import dev.sentinel.domain.agent.IntegrationResult;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ClaudeCodeIntegrationTest {
  private static final String SETTINGS_PATH = ".claude/settings.json";
  private static final String EDIT_WRITE_PATH = ".claude/hooks/sentinel-edit-write";
  private static final String POST_TOOL_USE = "PostToolUse";
  private static final String HOOKS_KEY = "hooks";

  @Test
  void installsIdempotentlyAndRemovesOwnedArtifacts() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-claude");
    final ClaudeCodeIntegration integration = new ClaudeCodeIntegration();

    final IntegrationResult created = integration.integrate(root, false);
    assertThat(created.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    final Path settings = root.resolve(SETTINGS_PATH);
    final Path hook = root.resolve(EDIT_WRITE_PATH);
    assertThat(settings).hasContent(Files.readString(settings));
    assertThat(Files.readString(settings))
        .contains(POST_TOOL_USE, "^(Edit|Write|MultiEdit)$", ClaudeCodeIntegration.SETTINGS_MARKER);
    assertThat(Files.readString(hook))
        .contains(
            ClaudeCodeIntegration.HOOK_MARKER,
            "exec sentinel hook claude-code --learn-after 3 --exclude-spec --event-stdin"
                + " -C \"${CLAUDE_PROJECT_DIR:-.}\"");
    final JsonTree hooks = JsonTree.parse(Files.readString(settings)).get(HOOKS_KEY);
    assertThat(hooks.get(POST_TOOL_USE).get(0).get(HOOKS_KEY).get(0).get("command").asString())
        .isEqualTo("${CLAUDE_PROJECT_DIR}/" + EDIT_WRITE_PATH);
    assertThat(hooks.get("Stop").get(0).get(HOOKS_KEY).get(0).get("command").asString())
        .isEqualTo("${CLAUDE_PROJECT_DIR}/" + EDIT_WRITE_PATH);
    assertThat(integration.integrate(root, false).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
    assertThat(integration.integrate(root, true).status())
        .isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(settings).exists();
    assertThat(Files.readString(settings))
        .doesNotContain("sentinelOwnership", "sentinel-edit-write");
    assertThat(hook).doesNotExist();
  }

  @Test
  void preservesAndMergesUserOwnedSettings() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-claude-user-settings");
    final Path settings = root.resolve(SETTINGS_PATH);
    Files.createDirectories(settings.getParent());
    Files.writeString(settings, "{\"hooks\":{}}\n");

    final IntegrationResult result = new ClaudeCodeIntegration().integrate(root, false);
    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(Files.readString(settings)).contains("sentinelOwnership", POST_TOOL_USE, "Stop");
    assertThat(root.resolve(EDIT_WRITE_PATH)).exists();
  }

  @Test
  void mergesExistingSettingsAndHooksWithoutDuplicatingSentinelEntries() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-claude-merge");
    final Path settings = root.resolve(SETTINGS_PATH);
    Files.createDirectories(settings.getParent());
    Files.writeString(
        settings,
        """
        {
          "theme": {"name": "dark"},
          "hooks": {
            "PostToolUse": [
              {"matcher": "Bash", "hooks": [{"type": "command", "command": "user-hook"}]}
            ]
          }
        }
        """);

    final ClaudeCodeIntegration integration = new ClaudeCodeIntegration();
    assertThat(integration.integrate(root, false).status())
        .isEqualTo(IntegrationResult.Status.CHANGED);
    final JsonTree merged = JsonTree.parse(Files.readString(settings));
    assertThat(merged.get("theme").get("name").asString()).isEqualTo("dark");
    assertThat(merged.get(HOOKS_KEY).get(POST_TOOL_USE).size()).isEqualTo(2);
    assertThat(
            merged
                .get(HOOKS_KEY)
                .get(POST_TOOL_USE)
                .get(0)
                .get(HOOKS_KEY)
                .get(0)
                .get("command")
                .asString())
        .isEqualTo("user-hook");
    assertThat(integration.integrate(root, false).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
    assertThat(JsonTree.parse(Files.readString(settings)).get(HOOKS_KEY).get(POST_TOOL_USE).size())
        .isEqualTo(2);
  }

  @Test
  void malformedSettingsArePreservedWithoutWritingAnyIntegrationFiles() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-claude-malformed");
    final Path settings = root.resolve(SETTINGS_PATH);
    Files.createDirectories(settings.getParent());
    Files.writeString(settings, "{not-json");

    final IntegrationResult result = new ClaudeCodeIntegration().integrate(root, false);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CONFLICT);
    assertThat(settings).hasContent("{not-json");
    assertThat(root.resolve(EDIT_WRITE_PATH)).doesNotExist();
  }

  @Test
  void nonObjectSettingsArePreservedWithoutWritingAnyIntegrationFiles() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-claude-nonobject");
    final Path settings = root.resolve(SETTINGS_PATH);
    Files.createDirectories(settings.getParent());
    Files.writeString(settings, "[]");

    final IntegrationResult result = new ClaudeCodeIntegration().integrate(root, false);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CONFLICT);
    assertThat(settings).hasContent("[]");
    assertThat(root.resolve(EDIT_WRITE_PATH)).doesNotExist();
  }

  @Test
  void removalPreservesUserSettingsAndUserHooks() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-claude-remove-merge");
    final Path settings = root.resolve(SETTINGS_PATH);
    Files.createDirectories(settings.getParent());
    Files.writeString(
        settings,
        """
        {
          "theme": "dark",
          "hooks": {
            "PostToolUse": [
              {"matcher": "Bash", "hooks": [{"type": "command", "command": "user-hook"}]}
            ]
          }
        }
        """);
    final ClaudeCodeIntegration integration = new ClaudeCodeIntegration();
    integration.integrate(root, false);

    assertThat(integration.integrate(root, true).status())
        .isEqualTo(IntegrationResult.Status.REMOVED);

    final JsonTree remaining = JsonTree.parse(Files.readString(settings));
    assertThat(remaining.has("sentinelOwnership")).isFalse();
    assertThat(remaining.get("theme").asString()).isEqualTo("dark");
    assertThat(remaining.get(HOOKS_KEY).get(POST_TOOL_USE).size()).isEqualTo(1);
    assertThat(
            remaining
                .get(HOOKS_KEY)
                .get(POST_TOOL_USE)
                .get(0)
                .get(HOOKS_KEY)
                .get(0)
                .get("command")
                .asString())
        .isEqualTo("user-hook");
  }

  @Test
  void removalKeepsSettingsFileWhenOnlySentinelSettingsWerePresent() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-claude-remove-empty");
    final ClaudeCodeIntegration integration = new ClaudeCodeIntegration();
    integration.integrate(root, false);
    final Path settings = root.resolve(SETTINGS_PATH);

    assertThat(integration.integrate(root, true).status())
        .isEqualTo(IntegrationResult.Status.REMOVED);

    assertThat(settings).exists();
    assertThat(JsonTree.parse(Files.readString(settings)).get(HOOKS_KEY).size()).isZero();
  }

  @Test
  void upgradesOutdatedOwnedHookAndReportsItChanged() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-claude-upgrade");
    final Path settings = root.resolve(SETTINGS_PATH);
    final Path hook = root.resolve(EDIT_WRITE_PATH);
    Files.createDirectories(hook.getParent());
    Files.writeString(
        settings, "{\"sentinelOwnership\":\"" + ClaudeCodeIntegration.SETTINGS_MARKER + "\"}\n");
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
    assertThat(Files.readString(settings)).contains(POST_TOOL_USE);
    assertThat(new ClaudeCodeIntegration().integrate(root, false).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
  }

  @Test
  void refreshesPreviousOwnedSettingsWithoutStopHook() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-claude-stop-upgrade");
    final Path settings = root.resolve(SETTINGS_PATH);
    final Path hook = root.resolve(EDIT_WRITE_PATH);
    Files.createDirectories(hook.getParent());
    Files.writeString(
        settings,
        """
        {
          "sentinelOwnership": "%s",
          "hooks": {
            "PostToolUse": [
              {
                "matcher": "^(Edit|Write|MultiEdit)$",
                "hooks": [{"type": "command", "command": ".claude/hooks/sentinel-edit-write"}]
              }
            ]
          }
        }
        """
            .formatted(ClaudeCodeIntegration.SETTINGS_MARKER));
    Files.writeString(
        hook,
        "#!/bin/sh\n# "
            + ClaudeCodeIntegration.HOOK_MARKER
            + "\n# Event payload is intentionally ignored; Sentinel checks the current project state.\n"
            + "exec sentinel hook claude-code --learn-after 3\n");

    final IntegrationResult result = new ClaudeCodeIntegration().integrate(root, false);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(result.changed()).containsExactlyInAnyOrder(settings.toString(), hook.toString());
    assertThat(Files.readString(settings))
        .contains("\"Stop\"", ClaudeCodeIntegration.SETTINGS_MARKER);
    assertThat(Files.readString(hook)).contains("--exclude-spec --event-stdin");
  }
}
