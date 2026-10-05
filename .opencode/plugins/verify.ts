const EDIT_TOOLS = new Set(["edit", "write", "multiedit", "patch"])

type ToolInput = Readonly<Record<string, unknown>>

interface ToolExecutionEvent {
  readonly status: "completed" | "error"
  readonly tool: string
  readonly input: ToolInput
}

interface PluginContext {
  readonly location: {
    readonly project: { readonly canonical: string }
  }
  readonly tool: {
    hook(
      name: "execute.after",
      callback: (event: ToolExecutionEvent) => void,
    ): Promise<unknown>
  }
}

function targetsMainSource(input: ToolInput): boolean {
  if (!input || typeof input !== "object") return false
  const values: unknown[] = Object.values(input)
  return values.some((value) => {
    if (typeof value !== "string") return false
    const path = value.replaceAll("\\", "/")
    return path.includes("/src/main/") || path.startsWith("src/main/")
  })
}

export default {
  id: "sentinel.quality-check",
  async setup(ctx: PluginContext): Promise<void> {
    await ctx.tool.hook("execute.after", (event: ToolExecutionEvent): void => {
      if (event.status !== "completed") return
      if (!EDIT_TOOLS.has(event.tool.toLowerCase())) return
      if (!targetsMainSource(event.input)) return

      const result = Bun.spawnSync(["./verify-quality.sh"], {
        cwd: ctx.location.project.canonical,
        stdout: "pipe",
        stderr: "pipe",
      })
      const stdout = new TextDecoder().decode(result.stdout ?? new Uint8Array())
      const stderr = new TextDecoder().decode(result.stderr ?? new Uint8Array())
      if (result.exitCode !== 0) {
        throw new Error(stdout || stderr || "Sentinel quality check failed")
      }
    })
  },
}
