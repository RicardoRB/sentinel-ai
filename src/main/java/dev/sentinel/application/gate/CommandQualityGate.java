package dev.sentinel.application.gate;

import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.project.Project;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.QualityGate;

import java.util.List;

/** Generic process-backed gate used by built-in and repository-defined gate registrations. */
public final class CommandQualityGate implements QualityGate {
    private final String name;
    private final CommandExecutor executor;
    private final List<String> command;

    public CommandQualityGate(String name, CommandExecutor executor, List<String> command) {
        this.name = name;
        this.executor = executor;
        this.command = List.copyOf(command);
    }

    @Override public String name() { return name; }

    @Override public GateResult execute(Project project) {
        return GateResult.from(name, command, executor.execute(command, project.root()));
    }
}
