export type Outcome = "pass" | "fail" | "blocked" | "unsupported";
export type EvidenceLevel = "deterministic" | "automated-live" | "witnessed-live";

export const EVIDENCE_SCHEMA_VERSION = 1;
const MAX_TEXT = 400;

export interface Assertion {
  name: string;
  passed: boolean;
  detail?: string;
}

/** What a driver observed; normalization decides the outcome that may be reported. */
export interface ScenarioObservation {
  harness: string;
  scenario: string;
  level: EvidenceLevel;
  claimed: Outcome;
  assertions: Assertion[];
  durationMs: number;
  runtimeVersion?: string;
  sourceRevision?: string;
  prerequisite?: string;
  unsupportedProvenance?: string;
  driverImplemented?: boolean;
}

export interface ScenarioRecord {
  schemaVersion: number;
  harness: string;
  scenario: string;
  level: EvidenceLevel;
  outcome: Outcome;
  reason: string;
  executedAssertions: number;
  assertions: Assertion[];
  durationMs: number;
  runtimeVersion?: string;
  sourceRevision?: string;
}

export interface Expectation {
  /** Exact pinned runtime version; evidence from any other version is stale. */
  runtimeVersion?: string;
  /** Revision under test; evidence from any other revision is stale. */
  sourceRevision?: string;
}

/**
 * Normalizes an observation so that only executed, passing assertions from the expected runtime
 * and revision can report a pass. Missing prerequisites and unimplemented drivers are blocked;
 * unsupported needs provenance.
 */
export function record(observation: ScenarioObservation, expected: Expectation = {}): ScenarioRecord {
  const assertions = observation.assertions.map((assertion) => ({
    name: redact(assertion.name),
    passed: assertion.passed,
    ...(assertion.detail === undefined ? {} : { detail: redact(assertion.detail) }),
  }));
  const failed = assertions.filter((assertion) => !assertion.passed);
  const [outcome, reason] = decide(observation, expected, assertions.length, failed);
  return {
    schemaVersion: EVIDENCE_SCHEMA_VERSION,
    harness: observation.harness,
    scenario: observation.scenario,
    level: observation.level,
    outcome,
    reason: redact(reason),
    executedAssertions: assertions.length,
    assertions,
    durationMs: observation.durationMs,
    ...(observation.runtimeVersion ? { runtimeVersion: observation.runtimeVersion } : {}),
    ...(observation.sourceRevision ? { sourceRevision: observation.sourceRevision } : {}),
  };
}

function decide(
  observation: ScenarioObservation,
  expected: Expectation,
  executed: number,
  failed: Assertion[],
): [Outcome, string] {
  if (observation.driverImplemented === false) return ["blocked", "verification driver is not implemented"];
  if (observation.prerequisite) return ["blocked", `missing prerequisite: ${observation.prerequisite}`];
  if (observation.claimed === "unsupported") {
    return observation.unsupportedProvenance
      ? ["unsupported", `capability absent: ${observation.unsupportedProvenance}`]
      : ["blocked", "unsupported outcome lacks capability provenance"];
  }
  if (failed.length) return ["fail", `failed assertions: ${failed.map((a) => a.name).join(", ")}`];
  if (observation.claimed !== "pass") return [observation.claimed, `driver reported ${observation.claimed}`];
  if (executed === 0) return ["fail", "no assertions were executed"];
  if (expected.runtimeVersion && observation.level !== "deterministic") {
    if (observation.runtimeVersion !== expected.runtimeVersion) {
      return ["blocked", `stale evidence: runtime ${observation.runtimeVersion ?? "unknown"} is not ${expected.runtimeVersion}`];
    }
  }
  if (expected.sourceRevision && observation.sourceRevision !== expected.sourceRevision) {
    return ["blocked", `stale evidence: revision ${observation.sourceRevision ?? "unknown"} is not ${expected.sourceRevision}`];
  }
  return ["pass", `${executed} assertions passed`];
}

export interface HarnessSummary {
  harness: string;
  outcome: Outcome;
  scenarios: ScenarioRecord[];
}

export interface MatrixReport {
  schemaVersion: number;
  level: EvidenceLevel;
  success: boolean;
  exitCode: number;
  harnesses: HarnessSummary[];
}


/**
 * Aggregates a requested matrix. A requested harness without records is blocked, records at a
 * different evidence level never count, and any fail or blocked outcome makes the matrix
 * non-successful while preserving each individual outcome.
 */
export function aggregate(
  records: ScenarioRecord[],
  requested: string[],
  level: EvidenceLevel,
): MatrixReport {
  const harnesses = requested.map((harness): HarnessSummary => {
    const scenarios = records.filter((r) => r.harness === harness && r.level === level);
    if (scenarios.length === 0) return { harness, outcome: "blocked", scenarios };
    const has = (outcome: Outcome) => scenarios.some((r) => r.outcome === outcome);
    // Evidenced-unsupported scenarios do not fail a harness, but a harness with no executed pass
    // has no compatibility evidence at all.
    const outcome: Outcome = has("fail") ? "fail" : has("blocked") ? "blocked" : has("pass") ? "pass" : "unsupported";
    return { harness, outcome, scenarios };
  });
  const success = harnesses.length > 0 && harnesses.every((h) => h.outcome === "pass");
  return { schemaVersion: EVIDENCE_SCHEMA_VERSION, level, success, exitCode: success ? 0 : 1, harnesses };
}

const SECRET_PATTERNS: RegExp[] = [
  /\b(sk-[A-Za-z0-9_-]{8,})/g,
  /\b(gh[pousr]_[A-Za-z0-9]{8,})/g,
  /\b(AIza[0-9A-Za-z_-]{8,})/g,
  /\b([A-Z0-9_]*(?:KEY|TOKEN|SECRET|PASSWORD)[A-Z0-9_]*)=\S+/g,
  /(Bearer\s+)[A-Za-z0-9._-]+/g,
];

/** Removes credential-shaped values and bounds the length of recorded diagnostic text. */
export function redact(text: string): string {
  let safe = text;
  for (const pattern of SECRET_PATTERNS) {
    safe = safe.replace(pattern, (match, group: string) =>
      match.includes("=") ? `${group}=[redacted]` : match.startsWith("Bearer") ? `${group}[redacted]` : "[redacted]",
    );
  }
  return safe.length > MAX_TEXT ? `${safe.slice(0, MAX_TEXT)}…[truncated]` : safe;
}
