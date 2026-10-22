package dev.ward.infrastructure.cli.gate;

/**
 * The renderers for each {@code check --format}.
 *
 * @param text the human-readable progress and summary
 * @param json the machine-readable JSON report
 * @param sarif the SARIF 2.1.0 log
 */
public record ReportRenderers(
    TextReportRenderer text, JsonReportRenderer json, SarifReportRenderer sarif) {}
