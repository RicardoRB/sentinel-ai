import { readFileSync } from "node:fs";
import { join } from "node:path";

export const CAPABILITIES = ["post-edit", "feedback", "pre-tool-guard", "turn-end"] as const;
export type Capability = (typeof CAPABILITIES)[number];
export type CapabilitySupport = "supported" | "unsupported";

export interface Runtime {
  executable: string;
  executableEnv: string;
  version: string;
  versionCommand: string[];
  versionPattern: string;
  source: string;
  install: string[];
}

export interface ContractPackage {
  name: string;
  version: string;
  matchesRuntime: boolean;
}

export interface Harness {
  id: string;
  label: string;
  runtime: Runtime;
  contractPackages: ContractPackage[];
  provenance: string[];
  platforms: string[];
  discovery: string[];
  capabilities: Record<Capability, CapabilitySupport>;
  capabilityNotes?: Partial<Record<Capability, string>>;
  verification: { mode: "automated" | "witnessed"; prerequisites: string[] };
}

export interface Manifest {
  schemaVersion: number;
  resolvedOn: string;
  harnesses: Harness[];
}

export const REPO_ROOT = join(import.meta.dir, "..", "..");
export const MANIFEST_PATH = join(REPO_ROOT, "compatibility", "manifest.json");

const SUPPORTED_AGENTS = join(
  REPO_ROOT,
  "src/main/java/dev/ward/domain/agent/SupportedAgents.java",
);

// Exact release identities: semantic versions (optionally with a pre-release or build suffix) or
// date-stamped builds. Ranges, wildcards and dist-tags are floating and never accepted.
const EXACT_VERSION = /^(\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?|\d{4}\.\d{2}\.\d{2}-[0-9a-f]+)$/;

export function loadManifest(path = MANIFEST_PATH): Manifest {
  return JSON.parse(readFileSync(path, "utf8")) as Manifest;
}

/** Reads the registered harness IDs from Ward's canonical adapter registry. */
export function registeredHarnessIds(source = readFileSync(SUPPORTED_AGENTS, "utf8")): string[] {
  return [...source.matchAll(/new Definition\("([^"]+)"/g)].map((match) => match[1]);
}

export function isExactVersion(version: unknown): boolean {
  return typeof version === "string" && EXACT_VERSION.test(version);
}

export function findHarness(manifest: Manifest, id: string): Harness {
  const harness = manifest.harnesses.find((candidate) => candidate.id === id);
  if (!harness) throw new Error(`no compatibility manifest entry for ${id}`);
  return harness;
}

/** Returns every inventory and pinning problem; an empty list means the manifest is valid. */
export function validateManifest(manifest: Manifest, registered: string[]): string[] {
  const errors: string[] = [];
  if (manifest.schemaVersion !== 1) errors.push(`unsupported schemaVersion ${manifest.schemaVersion}`);
  const ids = manifest.harnesses.map((harness) => harness.id);
  for (const id of new Set(ids.filter((id, index) => ids.indexOf(id) !== index))) {
    errors.push(`duplicate manifest entry ${id}`);
  }
  for (const id of registered.filter((id) => !ids.includes(id))) {
    errors.push(`registered adapter ${id} has no compatibility manifest entry`);
  }
  for (const id of ids.filter((id) => !registered.includes(id))) {
    errors.push(`manifest entry ${id} is not a registered adapter`);
  }
  for (const harness of manifest.harnesses) errors.push(...validateHarness(harness));
  return errors;
}

function validateHarness(harness: Harness): string[] {
  const errors: string[] = [];
  const at = (message: string) => errors.push(`${harness.id}: ${message}`);
  const runtime = harness.runtime;
  if (!runtime?.executable) at("runtime executable is required");
  if (!/^WARD_COMPAT_[A-Z_]+_BIN$/.test(runtime?.executableEnv ?? "")) {
    at("runtime executableEnv must be WARD_COMPAT_<ID>_BIN");
  }
  if (!isExactVersion(runtime?.version)) at(`runtime version ${runtime?.version} is not an exact release`);
  if (!Array.isArray(runtime?.versionCommand) || runtime.versionCommand.length === 0) {
    at("runtime versionCommand is required");
  }
  try {
    // An empty alternative always matches, exposing the number of capturing groups.
    const groups = new RegExp(`${runtime?.versionPattern ?? ""}|`).exec("")?.length ?? 1;
    if (!runtime?.versionPattern || groups < 2) {
      at("runtime versionPattern must capture the version");
    }
  } catch {
    at("runtime versionPattern is not a valid expression");
  }
  if (!isHttps(runtime?.source)) at("runtime source must be an https URL");
  errors.push(...validateInstall(harness).map((message) => `${harness.id}: ${message}`));
  for (const contract of harness.contractPackages ?? []) {
    if (!isExactVersion(contract.version)) {
      at(`contract package ${contract.name}@${contract.version} is not an exact release`);
    } else if (contract.matchesRuntime && contract.version !== runtime?.version) {
      at(`contract package ${contract.name}@${contract.version} does not match runtime ${runtime?.version}`);
    }
  }
  if (!harness.provenance?.length || !harness.provenance.every(isHttps)) {
    at("provenance must list authoritative https URLs");
  }
  if (!harness.discovery?.length) at("discovery paths are required");
  if (!harness.platforms?.length) at("platform constraints are required");
  for (const capability of CAPABILITIES) {
    const support = harness.capabilities?.[capability];
    if (support !== "supported" && support !== "unsupported") {
      at(`capability ${capability} must be supported or unsupported`);
    } else if (support === "unsupported" && !harness.capabilityNotes?.[capability]) {
      at(`unsupported capability ${capability} needs a provenance note`);
    }
  }
  const extra = Object.keys(harness.capabilities ?? {}).filter(
    (key) => !(CAPABILITIES as readonly string[]).includes(key),
  );
  if (extra.length) at(`unknown capabilities ${extra.join(", ")}`);
  const mode = harness.verification?.mode;
  if (mode !== "automated" && mode !== "witnessed") at("verification mode must be automated or witnessed");
  if (!harness.verification?.prerequisites?.length) at("verification prerequisites must be named");
  return errors;
}

function validateInstall(harness: Harness): string[] {
  const install = harness.runtime?.install;
  if (!Array.isArray(install)) return ["runtime install must be a list"];
  if (install.length === 0) return [];
  // Only isolated npm installs pinned to the runtime release are allowed; anything else could
  // change global state or resolve a floating version.
  const [tool, verb, prefix, dir, spec, ...rest] = install;
  if (tool !== "npm" || verb !== "install" || prefix !== "--prefix" || dir !== "${TOOLS}" || rest.length) {
    return ["runtime install must be `npm install --prefix ${TOOLS} <package>@<version>`"];
  }
  const at = spec?.lastIndexOf("@") ?? -1;
  if (at <= 0 || spec.slice(at + 1) !== harness.runtime.version) {
    return [`runtime install ${spec} is not pinned to ${harness.runtime.version}`];
  }
  return [];
}

function isHttps(value: unknown): boolean {
  return typeof value === "string" && value.startsWith("https://");
}
