package dev.ward.infrastructure.agent;

import dev.ward.domain.agent.GuardMode;
import dev.ward.domain.agent.IntegrationCapability;
import dev.ward.domain.agent.NativeHookInput;
import dev.ward.domain.agent.NativeHookOutcome;
import dev.ward.domain.json.JsonCodec;
import java.util.Set;
import org.apache.fory.json.annotation.JsonProperty;
import org.apache.fory.json.annotation.JsonType;

/** Native Amp TypeScript plugin integration contract. */
public final class AmpIntegration implements NativeHarnessAdapter {
  private static final NativeAgentIntegration.Definition DEFINITION =
      new NativeAgentIntegration.Definition(
          "amp",
          ".amp/plugins/ward.ts",
          ".amp/plugins/ward.sh",
          "tool.call,tool.result,agent.end",
          Set.of(
              IntegrationCapability.PRE_TOOL,
              IntegrationCapability.POST_EDIT,
              IntegrationCapability.TURN_END,
              IntegrationCapability.FEEDBACK,
              IntegrationCapability.APPROVED_ARTIFACT_GUARD));

  // Amp plugin API (@ampcode/plugin): tool.call returns allow or reject-and-continue, tool.result
  // may replace the output the model reads, and agent.end may continue the turn once.
  private static final String PLUGIN =
      """
      // %s
      import { spawn } from "node:child_process";
      import type { PluginAPI } from "@ampcode/plugin";

      // Calls .amp/plugins/ward.sh: "pre" runs the approved-artifact guard when it is enabled,
      // "post" runs the quality check. Edits and shell commands are identified with Amp's helpers.
      const HOOK = ".amp/plugins/ward.sh";
      const MAX = 8192;

      export default function (amp: PluginAPI) {
        const root = amp.system.workspaceRoot ? amp.helpers.filePathFromURI(amp.system.workspaceRoot) : null;
        const hook = (phase: string, payload: Record<string, unknown>) =>
          new Promise<{ code: number | null; stdout: string; stderr: string; error?: string }>((resolve) => {
            if (!root) return resolve({ code: null, stdout: "", stderr: "", error: "Amp has no workspace root" });
            let stdout = "";
            let stderr = "";
            const child = spawn(`${root}/${HOOK}`, [phase], { cwd: root, stdio: ["pipe", "pipe", "pipe"] });
            child.stdout.on("data", (chunk) => (stdout += chunk));
            child.stderr.on("data", (chunk) => (stderr += chunk));
            child.on("error", (error) => resolve({ code: null, stdout, stderr, error: String(error?.message ?? error) }));
            child.on("close", (code) => resolve({ code, stdout, stderr }));
            child.stdin.on("error", () => {});
            child.stdin.end(JSON.stringify(payload));
          });
        const modified = (event: Parameters<typeof amp.helpers.filesModifiedByToolCall>[0]) =>
          (amp.helpers.filesModifiedByToolCall(event) ?? []).map((uri) => amp.helpers.filePathFromURI(uri));

        amp.on("tool.call", async (event, ctx) => {
          const shell = amp.helpers.shellCommandFromToolCall(event);
          const payloads = modified(event).map((path) => ({ event: "tool.call", tool_name: event.tool, cwd: root, file_path: path }));
          if (shell) payloads.push({ event: "tool.call", tool_name: event.tool, cwd: shell.dir ?? root, command: shell.command } as never);
          for (const payload of payloads) {
            const result = await hook("pre", payload);
            if (result.error || result.code !== 0) {
              // Ward could not decide; the call proceeds, as documented.
              ctx.logger.log(`[ward guard] could not decide; allowing: ${(result.error ?? result.stderr).slice(0, 500)}`);
              continue;
            }
            let decision: { action?: string; message?: unknown } | null = null;
            try { decision = result.stdout.trim() ? JSON.parse(result.stdout) : null; } catch { decision = null; }
            if (decision?.action === "reject-and-continue") {
              return { action: "reject-and-continue", message: String(decision.message ?? "Refused by Ward.").slice(0, MAX) };
            }
          }
          return { action: "allow" };
        });

        amp.on("tool.result", async (event, ctx) => {
          // Failed or cancelled tool calls keep their original result and are not checked.
          if (event.status !== "done") return;
          const paths = modified(event);
          if (paths.length === 0) return;
          const targets = [paths[0], ...paths.slice(1).filter((path) => path.includes("openspec"))];
          const feedback: string[] = [];
          for (const path of targets) {
            const result = await hook("post", { event: "tool.result", tool_name: event.tool, cwd: root, file_path: path });
            if (result.error) {
              ctx.logger.log(`[ward] could not run: ${result.error.slice(0, 500)}`);
              return;
            }
            if (result.code !== 0) feedback.push((result.stderr || result.stdout).trim() || "Ward validation failed.");
          }
          if (feedback.length === 0) return;
          const original = typeof event.output === "string" ? event.output : JSON.stringify(event.output ?? "");
          return { status: "done", output: `${original}\\n\\n${[...new Set(feedback)].join("\\n").slice(0, MAX)}` };
        });

        amp.on("agent.end", async (event, ctx) => {
          if (event.status !== "done") return;
          const result = await hook("post", { event: "agent.end", cwd: root });
          if (result.error) {
            ctx.logger.log(`[ward] could not run: ${result.error.slice(0, 500)}`);
            return;
          }
          const text = (result.code === 0 ? result.stdout : result.stderr || result.stdout).trim();
          if (!text) return;
          // One corrective continuation per user message keeps an unfixable finding from looping.
          return { action: "continue", userMessage: `[ward]\\n${text.slice(0, MAX)}`, maxContinuations: 1 };
        });
      }
      """;

  @Override
  public NativeAgentIntegration.Definition definition() {
    return DEFINITION;
  }

  @Override
  public String configContent() {
    return PLUGIN.replace("%s", NativeHookText.marker(DEFINITION.id()));
  }

  @Override
  public String hookContent(final GuardMode guardMode) {
    return NativeHookText.phaseWrapper(DEFINITION.id(), DEFINITION.hookFile(), guardMode);
  }

  /** Returns the hook response that blocks the tool call and tells the agent {@code reason}. */
  public String denyResponse(final String reason) {
    return "{\"action\":\"reject-and-continue\",\"message\":" + NativeHookText.quote(reason) + "}";
  }

  @Override
  public String serialize(final NativeHookOutcome outcome) {
    return switch (outcome.kind()) {
      case DENY -> denyResponse(outcome.message());
      case FEEDBACK, ERROR -> outcome.message();
      case ALLOW -> "";
    };
  }

  @Override
  public NativeHookInput parse(final JsonCodec codec, final String payload) {
    return NativeHookText.parse(codec, payload, Payload.class, Payload::input);
  }

  @Override
  public boolean turnEnds(final NativeHookInput input) {
    return "agent.end".equals(input.eventName());
  }

  /** The Amp hook payload; its public fields mirror the event JSON. */
  @JsonType
  public static final class Payload {
    public String event;

    @JsonProperty("event_name")
    public String eventNameValue;

    @JsonProperty("hook_event_name")
    public String hookEventName;

    public String type;

    @JsonProperty("tool_name")
    public String toolNameValue;

    public String cwd;

    @JsonProperty("file_path")
    public String filePath;

    public String path;
    public String command;
    public String patch;
    public String patchText;
    public String skill;
    public String name;

    NativeHookInput input() {
      return new NativeHookInput(
          first(event, eventNameValue, hookEventName, type),
          first(toolNameValue, name),
          cwd == null ? "" : cwd,
          first(filePath, path),
          command,
          first(patch, patchText),
          skill,
          false,
          false);
    }

    private static String first(final String... values) {
      for (final String value : values) {
        if (value != null && !value.isBlank()) {
          return value;
        }
      }
      return null;
    }
  }
}
