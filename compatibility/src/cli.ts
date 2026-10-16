import { mkdirSync } from "node:fs";
import { join, resolve } from "node:path";
import { findHarness, loadManifest, REPO_ROOT } from "./manifest";
import { install, preflight } from "./preflight";
import { runLive } from "./live";
import { DRIVERS } from "../drivers";

const USAGE = [
  "usage: bun run src/cli.ts preflight [--install] [--tools <dir>] [--npm <path>] <harness-id>... | all",
  "       bun run src/cli.ts live [--tools <dir>] <harness-id>... | all",
].join("\n");

/**
 * Opt-in runtime preflight. It installs only into the isolated tools directory (default
 * compatibility/.tools), never globally, and exits non-zero unless every requested runtime is
 * present at its exact pinned version.
 */
async function main(args: string[]): Promise<number> {
  if (args[0] === "live") return live(args.slice(1));
  if (args[0] !== "preflight") {
    console.error(USAGE);
    return 2;
  }
  let toolsDir = join(REPO_ROOT, "compatibility", ".tools");
  let npm: string | undefined;
  let installRequested = false;
  const ids: string[] = [];
  for (let i = 1; i < args.length; i++) {
    if (args[i] === "--install") installRequested = true;
    else if (args[i] === "--tools") toolsDir = resolve(args[++i]);
    else if (args[i] === "--npm") npm = resolve(args[++i]);
    else ids.push(args[i]);
  }
  const manifest = loadManifest();
  const requested = ids.length === 1 && ids[0] === "all" ? manifest.harnesses.map((h) => h.id) : ids;
  if (requested.length === 0) {
    console.error(USAGE);
    return 2;
  }
  const home = join(toolsDir, "home");
  mkdirSync(home, { recursive: true });
  let ready = 0;
  for (const id of requested) {
    const harness = findHarness(manifest, id);
    if (installRequested) {
      const installed = await install(harness, { toolsDir, npm: npm ?? Bun.which("npm") ?? "npm", home });
      if (!installed.installed) console.log(JSON.stringify({ harness: id, status: "blocked", prerequisite: installed.prerequisite }));
    }
    const result = await preflight(harness, { toolsDir, env: process.env, home });
    if (result.status === "ready") ready++;
    console.log(JSON.stringify(result));
  }
  return ready === requested.length ? 0 : 1;
}

/**
 * Opt-in live verification. Prints one JSON matrix report; exits 1 unless every requested
 * harness passed in its pinned real runtime.
 */
async function live(args: string[]): Promise<number> {
  let toolsDir = join(REPO_ROOT, "compatibility", ".tools");
  const ids: string[] = [];
  for (let i = 0; i < args.length; i++) {
    if (args[i] === "--tools") toolsDir = resolve(args[++i]);
    else ids.push(args[i]);
  }
  const manifest = loadManifest();
  const requested = ids.length === 1 && ids[0] === "all" ? manifest.harnesses.map((h) => h.id) : ids;
  if (requested.length === 0) {
    console.error(USAGE);
    return 2;
  }
  const home = join(toolsDir, "home");
  mkdirSync(home, { recursive: true });
  const report = await runLive(requested, { env: process.env, toolsDir, home, drivers: DRIVERS, manifest });
  console.log(JSON.stringify(report, null, 2));
  return report.exitCode;
}

process.exit(await main(process.argv.slice(2)));
