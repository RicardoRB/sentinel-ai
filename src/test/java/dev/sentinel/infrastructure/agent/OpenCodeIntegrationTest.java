package dev.sentinel.infrastructure.agent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.agent.IntegrationResult;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OpenCodeIntegrationTest {
  @Test
  void integratesAndRemovesOnlyOwnedContent() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-opencode");
    final OpenCodeIntegration integration = new OpenCodeIntegration();
    final IntegrationResult created = integration.integrate(root, false);
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
    assertThat(integration.integrate(root, false).status())
        .isEqualTo(IntegrationResult.Status.ALREADY_PRESENT);
    assertThat(integration.integrate(root, true).status())
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
                .integrate(target.getParent().getParent().getParent(), false)
                .status())
        .isEqualTo(IntegrationResult.Status.CONFLICT);
  }

  @Test
  void refusesToInstallPluginWhenItConflictsWithoutTouchingCommand() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-opencode-plugin-conflict");
    final Path plugin = root.resolve(".opencode/plugins/sentinel-edit-write.js");
    Files.createDirectories(plugin.getParent());
    Files.writeString(plugin, "user-owned");

    final IntegrationResult result = new OpenCodeIntegration().integrate(root, false);
    assertThat(result.status()).isEqualTo(IntegrationResult.Status.CONFLICT);
    assertThat(root.resolve(".opencode/commands/sentinel-check.md")).doesNotExist();
    assertThat(plugin).hasContent("user-owned");
  }

  @Test
  void pluginValidatesEditedSpecPathsAfterTheQualityCheck() throws Exception {
    final Path root = Files.createTempDirectory("sentinel-opencode-spec");
    new OpenCodeIntegration().integrate(root, false);

    final String plugin =
        Files.readString(root.resolve(".opencode/plugins/sentinel-edit-write.js"));

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

    final String plugin =
        Files.readString(root.resolve(".opencode/plugins/sentinel-edit-write.js"));

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
    final Path plugin = root.resolve(".opencode/plugins/sentinel-edit-write.js");
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
}
