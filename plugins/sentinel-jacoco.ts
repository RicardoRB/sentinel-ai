import { Plugin } from "@opencode/plugin"

const DEFAULT_THRESHOLD = 0.8
const MUTATING_TOOLS = new Set(["edit", "write", "apply_patch", "patch"])

type PluginOptions = {
  threshold?: number
  command?: string[]
}

type CommandResult = {
  exitCode: number
  stdout: string
  stderr: string
}

function run(command: string[], cwd: string): CommandResult {
  const result = Bun.spawnSync(command, {
    cwd,
    stdout: "pipe",
    stderr: "pipe",
  })

  const decode = (value: Uint8Array | undefined) =>
    new TextDecoder().decode(value ?? new Uint8Array())

  return {
    exitCode: result.exitCode,
    stdout: decode(result.stdout),
    stderr: decode(result.stderr),
  }
}

function instructionCoverage(csv: string): { covered: number; total: number } {
  const rows = csv.trim().split("\n").slice(1).filter(Boolean)
  return rows.reduce(
    (summary, row) => {
      const columns = row.split(",")
      const missed = Number(columns[3])
      const covered = Number(columns[4])
      if (!Number.isFinite(missed) || !Number.isFinite(covered)) return summary
      return {
        covered: summary.covered + covered,
        total: summary.total + missed + covered,
      }
    },
    { covered: 0, total: 0 },
  )
}

function toolName(input: unknown): string {
  if (!input || typeof input !== "object") return ""
  const value = input as { tool?: unknown; name?: unknown }
  return String(value.tool ?? value.name ?? "").toLowerCase()
}

function eventToolName(event: unknown): string {
  if (!event || typeof event !== "object") return ""
  const value = event as { tool?: unknown; name?: unknown; input?: unknown; args?: unknown }
  return String(value.tool ?? value.name ?? toolName(value.input ?? value.args)).toLowerCase()
}

export default Plugin.define({
  id: "sentinel.jacoco",
  async setup(ctx) {
    const options = (ctx.options ?? {}) as PluginOptions
    const threshold = options.threshold ?? DEFAULT_THRESHOLD
    const command = options.command ?? ["./verify-quality.sh"]
    const projectRoot = ctx.location.project.directory

    if (!Number.isFinite(threshold) || threshold < 0 || threshold > 1) {
      throw new Error("sentinel.jacoco: threshold must be between 0 and 1")
    }

    await ctx.tool.hook("execute.after", async (event) => {
      if (!MUTATING_TOOLS.has(eventToolName(event))) return
      if (event.status === "error") return

      const result = run(command, projectRoot)
      if (result.exitCode !== 0) {
        throw new Error(
          `JaCoCo quality gate failed while running ${command.join(" ")}:\n${result.stderr || result.stdout}`,
        )
      }

      const report = await Bun.file(`${projectRoot}/target/site/jacoco/jacoco.csv`).text().catch(() => "")
      const coverage = instructionCoverage(report)
      const percentage = coverage.total === 0 ? 0 : coverage.covered / coverage.total

      if (coverage.total === 0 || percentage < threshold) {
        throw new Error(
          `JaCoCo coverage is ${(percentage * 100).toFixed(2)}% `
            + `(${coverage.covered}/${coverage.total} instructions); `
            + `${(threshold * 100).toFixed(0)}% is required before finishing the task.`,
        )
      }

      console.log(
        `sentinel.jacoco: coverage gate passed at ${(percentage * 100).toFixed(2)}% `
          + `(${coverage.covered}/${coverage.total} instructions)`,
      )
    })
  },
})
