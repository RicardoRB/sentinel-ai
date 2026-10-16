import { describe, expect, test } from "bun:test";
import { isExactVersion, loadManifest, registeredHarnessIds, validateManifest, type Manifest } from "../src/manifest";

const manifest = loadManifest();
const registered = registeredHarnessIds();
const clone = (): Manifest => structuredClone(manifest);

describe("compatibility inventory", () => {
  test("covers exactly the ten registered adapters and is valid", () => {
    expect(registered).toEqual([
      "opencode",
      "claude-code",
      "codex",
      "cursor",
      "copilot",
      "gemini",
      "windsurf",
      "cline",
      "factory-droid",
      "amp",
    ]);
    expect(validateManifest(manifest, registered)).toEqual([]);
  });

  test("fails for a registered adapter missing from the manifest", () => {
    expect(validateManifest(manifest, [...registered, "new-harness"])).toContain(
      "registered adapter new-harness has no compatibility manifest entry",
    );
    const withoutAmp = clone();
    withoutAmp.harnesses = withoutAmp.harnesses.filter((h) => h.id !== "amp");
    expect(validateManifest(withoutAmp, registered)).toContain("registered adapter amp has no compatibility manifest entry");
  });

  test("rejects entries that are not registered and duplicates", () => {
    const extra = clone();
    extra.harnesses.push({ ...extra.harnesses[0] });
    expect(validateManifest(extra, registered)).toContain("duplicate manifest entry opencode");
  });

  test.each(["latest", "^2.0.30", "~1.0.0", "1.x", "*", ">=0.1.0", "", "2.0"])("rejects floating version %p", (version) => {
    expect(isExactVersion(version)).toBe(false);
    const floating = clone();
    floating.harnesses[2].runtime.version = version;
    expect(validateManifest(floating, registered).some((e) => e.startsWith("codex: runtime version"))).toBe(true);
  });

  test.each(["2.0.30", "0.0.1792123242-g03edc7", "2026.10.14-941492e"])("accepts exact release %p", (version) => {
    expect(isExactVersion(version)).toBe(true);
  });

  test("rejects a contract package that does not match its coupled runtime", () => {
    const mismatched = clone();
    mismatched.harnesses[0].contractPackages[0].version = "2.0.29";
    expect(validateManifest(mismatched, registered)).toContain(
      "opencode: contract package @opencode/plugin@2.0.29 does not match runtime 2.0.30",
    );
  });

  test("rejects an install that is global or not pinned to the runtime release", () => {
    const global = clone();
    global.harnesses[2].runtime.install = ["npm", "install", "-g", "@openai/codex@0.163.0"];
    expect(validateManifest(global, registered).some((e) => e.startsWith("codex: runtime install must be"))).toBe(true);
    const unpinned = clone();
    unpinned.harnesses[2].runtime.install = ["npm", "install", "--prefix", "${TOOLS}", "@openai/codex@latest"];
    expect(validateManifest(unpinned, registered)).toContain("codex: runtime install @openai/codex@latest is not pinned to 0.163.0");
  });

  test("an unsupported capability needs provenance and cannot be an unknown value", () => {
    const unexplained = clone();
    unexplained.harnesses[2].capabilities["pre-tool-guard"] = "unsupported";
    expect(validateManifest(unexplained, registered)).toContain("codex: unsupported capability pre-tool-guard needs a provenance note");
    const unknown = clone();
    (unknown.harnesses[2].capabilities as any).feedback = "maybe";
    expect(validateManifest(unknown, registered)).toContain("codex: capability feedback must be supported or unsupported");
  });

  test("a version pattern must capture the version", () => {
    const noGroup = clone();
    noGroup.harnesses[2].runtime.versionPattern = "^codex-cli \\S+$";
    expect(validateManifest(noGroup, registered)).toContain("codex: runtime versionPattern must capture the version");
  });
});
