package dev.sentinel.infrastructure.agent;

import dev.sentinel.domain.agent.AgentIntegration;
import dev.sentinel.domain.agent.IntegrationRequest;
import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.agent.OpenSpecGuidanceLayout;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Safe, ownership-marked integration for OpenCode command discovery and edit/write events. */
public final class OpenCodeIntegration implements AgentIntegration {
  public static final String MARKER = "<!-- sentinel-owned: opencode-quality-check v1 -->";
  public static final String PLUGIN_MARKER = "sentinel-owned: opencode-edit-write v1";
  private static final String COMMAND_FILE = ".opencode/commands/sentinel-check.md";
  private static final String PLUGIN_FILE = ".opencode/plugins/sentinel-edit-write.js";
  public static final String GUIDANCE_FILE =
      OpenSpecGuidanceLayout.forAgent("opencode").orElseThrow().guidanceFile();

  private static final String COMMAND =
      MARKER + "\n\nRun `sentinel check --format json --learn-after 3` and return the result.\n";
  private static final String PLUGIN =
      """
            // %s
            // OpenCode post-tool hook for Edit and Write operations.
            export default async ({ directory }) => ({
              "tool.execute.after": async (input, output) => {
                const tool = String(input?.tool ?? input?.name ?? "").toLowerCase();
                if (tool !== "edit" && tool !== "write" && tool !== "multiedit" && tool !== "patch") return;
                 const result = Bun.spawnSync(["sentinel", "check", "--format", "json", "--learn-after", "3"], {
                  cwd: directory,
                  stdout: "pipe",
                  stderr: "pipe"
                });
                 const text = new TextDecoder().decode(result.stdout || new Uint8Array());
                 let report;
                 try { report = JSON.parse(text); } catch { report = null; }
                 const prompts = report?.learning?.prompts?.map((prompt) => prompt.instruction).join("\\n") || "";
                 if (result.exitCode !== 0) {
                   output.error = (text || new TextDecoder().decode(result.stderr || new Uint8Array())) + (prompts ? "\\n" + prompts : "");
                   throw new Error(output.error || "Sentinel quality check failed");
                 }
                 if (prompts) output.output = (output.output || "") + "\\n" + prompts;
              }
            });
            """
          .replace("%s", PLUGIN_MARKER);

  @Override
  public String id() {
    return "opencode";
  }

  @Override
  public IntegrationResult integrate(final Path projectRoot, final IntegrationRequest request) {
    final Path root = projectRoot.toAbsolutePath().normalize();
    final List<IntegrationArtifacts.Target> targets = new ArrayList<>();
    if (request.coversHooks()) {
      targets.add(new IntegrationArtifacts.Target(root.resolve(COMMAND_FILE), MARKER, COMMAND));
      targets.add(
          new IntegrationArtifacts.Target(root.resolve(PLUGIN_FILE), PLUGIN_MARKER, PLUGIN));
    }
    if (request.coversGuidance()) {
      targets.add(
          new IntegrationArtifacts.Target(
              root.resolve(GUIDANCE_FILE), OpenSpecGuidance.MARKER, OpenSpecGuidance.content()));
    }
    final String subject =
        request.coversHooks() ? "OpenCode integration" : "OpenCode OpenSpec guidance";
    try {
      return request.removal()
          ? IntegrationArtifacts.removeAll(
              targets,
              root.resolve(".opencode"),
              "No Sentinel " + subject + " found.",
              "Removed Sentinel-owned " + subject + ".")
          : IntegrationArtifacts.installOrRefreshAll(
              targets,
              "Sentinel " + subject + " is already present.",
              request.coversHooks()
                  ? "Created Sentinel-owned OpenCode command and edit/write integration (or"
                      + " refreshed it)."
                  : "Created Sentinel-owned " + subject + " (or refreshed it).");
    } catch (IOException e) {
      throw new UncheckedIOException("Could not update OpenCode integration: " + e.getMessage(), e);
    }
  }
}
