const fixture = process.argv[process.argv.indexOf("--fixture") + 1];
const required = [fixture, process.env.OPENCODE_BIN ?? "opencode"];
if (!fixture) {
  console.error(
    "missing prerequisite: --fixture /absolute/path/to/trusted-fixture",
  );
  process.exit(2);
}
try {
  const stat = await Bun.file(
    `${fixture}/.opencode/plugins/sentinel-edit-write.js`,
  ).exists();
  if (!stat) throw new Error("generated quality plugin is missing");
  console.error(
    `smoke test requires a trusted pre-built fixture and OpenCode V2: ${required[1]}`,
  );
  console.error(
    "live runtime activation, edit feedback, guard refusal, idle limits, and reload cleanup were not verified",
  );
  process.exit(2);
} catch (error) {
  console.error(
    `fixture prerequisite unavailable: ${error instanceof Error ? error.message : String(error)}`,
  );
  process.exit(2);
}
