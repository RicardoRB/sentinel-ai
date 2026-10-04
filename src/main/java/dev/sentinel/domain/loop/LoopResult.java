package dev.sentinel.domain.loop;

import dev.sentinel.domain.gate.CheckReport;

import java.util.List;

public record LoopResult(LoopTerminalState state, int iterations, List<CheckReport> history, String message) {
    public LoopResult { history = List.copyOf(history); }
}
