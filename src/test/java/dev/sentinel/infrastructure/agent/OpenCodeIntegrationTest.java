package dev.sentinel.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.agent.GuardMode;
import dev.sentinel.domain.agent.IntegrationRequest;
import dev.sentinel.domain.agent.IntegrationResult;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OpenCodeIntegrationTest {
  private static final String COMMAND_PATH = ".opencode/commands/sentinel-check.md";
  private static final String PLUGIN_PATH = ".opencode/plugins/sentinel-edit-write.js";
  private static final String GUARD_PATH = ".opencode/plugins/sentinel-openspec-guard.js";
  private static final IntegrationRequest ENABLE_GUARD =
      new IntegrationRequest(false, GuardMode.ENABLE);
  private static final IntegrationRequest DISABLE_GUARD =
      new IntegrationRequest(false, GuardMode.DISABLE);

  @Test
  void integratesAndRemovesOnlyOwnedContent() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-opencode");
    final OpenCodeIntegration integration = new OpenCodeIntegration();
    final IntegrationResult created = integration.integrate(root, false);
    assertThat(created.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(Files.exists(root.resolve(COMMAND_PATH))).isTrue();
    assertThat(Files.readString(root.resolve(PLUGIN_PATH)))
        .contains(
            OpenCodeIntegration.PLUGIN_MARKER,
            "tool.execute.after",
            "sentinel",
            "--learn-after",
            "multiedit",
            "patch");
    assertThat(integration.integrate(root, false).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
    assertThat(integration.integrate(root, true).status())
        .isEqualTo(IntegrationResult.Status.REMOVED);
    assertThat(root.resolve(PLUGIN_PATH)).doesNotExist();
    assertThat(root.resolve(COMMAND_PATH)).doesNotExist();
  }

  @Test
  void refusesToOverwriteUnownedCommand() throws Exception {
    final Path target = Files.createTempDirectory("sentinel-opencode").resolve(COMMAND_PATH);
    Files.createDirectories(target.getParent());
    Files.writeString(target, "user-owned");
    assertThat(
            new OpenCodeIntegration()
                .integrate(target.getParent().getParent().getParent(), false)
                .status())
        .isEqualTo(IntegrationResult.Status.CONFLICT);
  }

  @Test
  void refusesToInstallPluginWhenItConflictsWithoutTouchingCommand() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-opencode-plugin-conflict");
    final Path plugin = root.resolve(PLUGIN_PATH);
    Files.createDirectories(plugin.getParent());
    Files.writeString(plugin, "user-owned");

    final IntegrationResult result = new OpenCodeIntegration().integrate(root, false);
    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CONFLICT);
    assertThat(root.resolve(COMMAND_PATH)).doesNotExist();
    assertThat(plugin).hasContent("user-owned");
  }

  @Test
  void pluginValidatesEditedSpecPathsAfterTheQualityCheck() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-opencode-spec");
    new OpenCodeIntegration().integrate(root, false);

    final String plugin = Files.readString(root.resolve(PLUGIN_PATH));

    assertThat(plugin)
        .contains(
            "new Set([\"edit\", \"write\", \"multiedit\", \"apply_patch\", \"patch\"])",
            "args.filePath",
            "args.path",
            "output?.metadata?.files",
            "file?.filePath",
            "file?.movePath",
            "[\"check\", \"--format\", \"json\", \"--exclude-spec\", \"--learn-after\", \"3\"]",
            "[\"spec\", \"validate\", \"--\", ...paths]",
            "throw new Error(output.error)");
  }

  @Test
  void pluginDeliversIdleFindingsOncePerTurnWithoutOverlap() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-opencode-idle");
    new OpenCodeIntegration().integrate(root, false);

    final String plugin = Files.readString(root.resolve(PLUGIN_PATH));

    assertThat(plugin)
        .contains(
            "event?.type !== \"session.idle\"",
            "running.has(sessionID)",
            "running.delete(sessionID)",
            "Bun.spawn([\"sentinel\", \"spec\", \"validate\"]",
            "!delivered.has(sessionID)",
            "client.session",
            ".prompt({ path: { id: sessionID }",
            "const FEEDBACK_MARKER = \"[sentinel spec validation]\"",
            "showToast",
            "variant: \"warning\"",
            "\"chat.message\"",
            "startsWith(FEEDBACK_MARKER)",
            "if (!own) delivered.delete(input?.sessionID)");
  }

  @Test
  void refreshesPreviousOwnedPluginAndKeepsItsMarker() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-opencode-refresh");
    final Path plugin = root.resolve(PLUGIN_PATH);
    Files.createDirectories(plugin.getParent());
    Files.writeString(
        plugin,
        "// "
            + OpenCodeIntegration.PLUGIN_MARKER
            + "\n// OpenCode post-tool hook for Edit and Write operations.\n"
            + "export default async () => ({});\n");

    final IntegrationResult result = new OpenCodeIntegration().integrate(root, false);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(result.changed()).contains(plugin.toString());
    assertThat(Files.readString(plugin))
        .startsWith("// " + OpenCodeIntegration.PLUGIN_MARKER)
        .contains("session.idle");
  }

  @Test
  void installsKeepsDisablesAndRemovesTheSeparateGuard() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-opencode-guard");
    final OpenCodeIntegration integration = new OpenCodeIntegration();
    final Path guard = root.resolve(GUARD_PATH);

    assertThat(integration.integrate(root, ENABLE_GUARD).status())
        .isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(Files.readString(guard))
        .contains(
            OpenCodeIntegration.GUARD_MARKER,
            "tool.execute.before",
            "apply_patch",
            "patchText",
            "workdir",
            "--shell-stdin",
            "--skill",
            "exitCode",
            "showToast");
    assertThat(integration.integrate(root, IntegrationRequest.INSTALL).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
    assertThat(integration.integrate(root, DISABLE_GUARD).status())
        .isEqualTo(IntegrationResult.Status.CHANGED);
    assertThat(guard).doesNotExist();
    assertThat(root.resolve(PLUGIN_PATH)).exists();
  }

  @Test
  void guardConflictPreventsTheOtherIntegrationFilesFromBeingWritten() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-opencode-guard-conflict");
    final Path guard = root.resolve(GUARD_PATH);
    Files.createDirectories(guard.getParent());
    Files.writeString(guard, "user-owned");

    final IntegrationResult result = new OpenCodeIntegration().integrate(root, ENABLE_GUARD);

    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CONFLICT);
    assertThat(root.resolve(COMMAND_PATH)).doesNotExist();
    assertThat(root.resolve(PLUGIN_PATH)).doesNotExist();
    assertThat(guard).hasContent("user-owned");
  }
}
