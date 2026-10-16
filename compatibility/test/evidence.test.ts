import { describe, expect, test } from "bun:test";
import { aggregate, record, redact, type ScenarioObservation } from "../src/evidence";

const live = (overrides: Partial<ScenarioObservation> = {}): ScenarioObservation => ({
  harness: "codex",
  scenario: "edit-check-feedback",
  level: "automated-live",
  claimed: "pass",
  assertions: [{ name: "native edit invoked sentinel", passed: true }],
  durationMs: 10,
  runtimeVersion: "0.163.0",
  sourceRevision: "abc123",
  ...overrides,
});
const expected = { runtimeVersion: "0.163.0", sourceRevision: "abc123" };

describe("scenario records", () => {
  test("executed passing assertions at the pinned version and revision pass", () => {
    expect(record(live(), expected).outcome).toBe("pass");
  });

  test("zero executed assertions never pass", () => {
    const result = record(live({ assertions: [] }), expected);
    expect(result.outcome).toBe("fail");
    expect(result.reason).toBe("no assertions were executed");
  });

  test("an unimplemented driver is blocked even if it claims a pass", () => {
    expect(record(live({ driverImplemented: false }), expected)).toMatchObject({
      outcome: "blocked",
      reason: "verification driver is not implemented",
    });
  });

  test("a missing prerequisite is blocked and named", () => {
    expect(record(live({ prerequisite: "OPENAI_API_KEY" }), expected)).toMatchObject({
      outcome: "blocked",
      reason: "missing prerequisite: OPENAI_API_KEY",
    });
  });

  test("stale runtime or revision evidence never reports a live pass", () => {
    expect(record(live({ runtimeVersion: "0.162.0" }), expected).outcome).toBe("blocked");
    expect(record(live({ runtimeVersion: undefined }), expected).outcome).toBe("blocked");
    expect(record(live({ sourceRevision: "old" }), expected).outcome).toBe("blocked");
  });

  test("unsupported requires capability provenance", () => {
    expect(record(live({ claimed: "unsupported" }), expected).outcome).toBe("blocked");
    expect(record(live({ claimed: "unsupported", unsupportedProvenance: "docs say no pre-hook" }), expected).outcome).toBe(
      "unsupported",
    );
  });

  test("a failed assertion fails regardless of the claimed outcome", () => {
    const result = record(live({ assertions: [{ name: "feedback delivered", passed: false }] }), expected);
    expect(result).toMatchObject({ outcome: "fail", reason: "failed assertions: feedback delivered" });
  });
});

describe("matrix aggregation", () => {
  const pass = (harness: string) => record(live({ harness }), expected);

  test("a mixed matrix is non-success and preserves each outcome", () => {
    const report = aggregate(
      [pass("codex"), record(live({ harness: "gemini", prerequisite: "GEMINI_API_KEY" }), expected), record(live({ harness: "amp", assertions: [{ name: "x", passed: false }] }), expected)],
      ["codex", "gemini", "amp"],
      "automated-live",
    );
    expect(report.success).toBe(false);
    expect(report.exitCode).toBe(1);
    expect(report.harnesses.map((h) => [h.harness, h.outcome])).toEqual([
      ["codex", "pass"],
      ["gemini", "blocked"],
      ["amp", "fail"],
    ]);
  });

  test("a requested harness without records is blocked, not skipped", () => {
    const report = aggregate([pass("codex")], ["codex", "cursor"], "automated-live");
    expect(report.harnesses[1]).toMatchObject({ harness: "cursor", outcome: "blocked" });
    expect(report.success).toBe(false);
  });

  test("deterministic evidence never satisfies a live matrix", () => {
    const deterministic = record(live({ level: "deterministic" }), expected);
    expect(deterministic.outcome).toBe("pass");
    expect(aggregate([deterministic], ["codex"], "automated-live").success).toBe(false);
  });

  test("witnessed evidence never satisfies an automated matrix", () => {
    const witnessed = record(live({ level: "witnessed-live" }), expected);
    expect(aggregate([witnessed], ["codex"], "automated-live").harnesses[0].outcome).toBe("blocked");
  });

  test("an all-unsupported harness has no evidence and is not a success", () => {
    const unsupported = record(live({ claimed: "unsupported", unsupportedProvenance: "no hooks" }), expected);
    const report = aggregate([unsupported], ["codex"], "automated-live");
    expect(report.harnesses[0].outcome).toBe("unsupported");
    expect(report.success).toBe(false);
  });

  test("an empty request is not a success", () => {
    expect(aggregate([], [], "automated-live").exitCode).toBe(1);
  });

  test("evidenced unsupported scenarios do not fail a passing harness", () => {
    const unsupported = record(live({ scenario: "guard", claimed: "unsupported", unsupportedProvenance: "post-hooks only" }), expected);
    expect(aggregate([pass("codex"), unsupported], ["codex"], "automated-live").success).toBe(true);
  });
});

describe("redaction", () => {
  test("credential-shaped values are removed", () => {
    const text = redact("OPENAI_API_KEY=sk-abcdefghijklmnop token ghp_abcdefghijkl Authorization: Bearer abc.def AIzaSyABCDEFGHIJ");
    expect(text).not.toContain("sk-abcdefghijklmnop");
    expect(text).not.toContain("ghp_abcdefghijkl");
    expect(text).not.toContain("abc.def");
    expect(text).not.toContain("AIzaSyABCDEFGHIJ");
    expect(text).toContain("OPENAI_API_KEY=[redacted]");
  });

  test("diagnostic text is bounded", () => {
    expect(redact("x".repeat(1000)).length).toBeLessThan(450);
  });

  test("records redact assertion details", () => {
    const result = record(live({ assertions: [{ name: "ok", passed: true, detail: "GEMINI_API_KEY=AIzaSyABCDEFGHIJ" }] }), expected);
    expect(result.assertions[0].detail).toBe("GEMINI_API_KEY=[redacted]");
  });
});
