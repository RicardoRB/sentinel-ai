package dev.sentinel.infrastructure.cli.gate;

import dev.sentinel.domain.gate.SeverityThreshold;

/**
 * The run settings a JSON report records alongside its checks.
 *
 * @param strict whether the strict profile's policies apply
 * @param failFast whether the run stopped after the first failing gate
 * @param severity the lowest finding severity included in the report
 */
public record JsonReportOptions(boolean strict, boolean failFast, SeverityThreshold severity) {}
