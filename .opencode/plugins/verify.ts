const EDIT_TOOLS = new Set(["edit", "write", "multiedit", "patch"])

type ToolInput = Readonly<Record<string, unknown>>

interface ToolOutput {
  error?: string
}

export default async ({ directory }: { directory: string }) => ({
  "tool.execute.after": async (
    input: ToolInput,
    output: ToolOutput,
  ): Promise<void> => {
    const tool = String(input.tool ?? input.name ?? "").toLowerCase()
    if (!EDIT_TOOLS.has(tool)) return

    const result = Bun.spawnSync(["./verify-quality.sh"], {
      cwd: directory,
      stdout: "pipe",
      stderr: "pipe",
    })
    const stdout = new TextDecoder().decode(result.stdout ?? new Uint8Array())
    const stderr = new TextDecoder().decode(result.stderr ?? new Uint8Array())
    if (result.exitCode !== 0) {
      output.error = stdout || stderr || "Sentinel quality check failed"
      throw new Error(output.error)
    }
  },
})
