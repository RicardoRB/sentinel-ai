package dev.sentinel.application.gate;

import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.project.Project;
import dev.sentinel.domain.gate.GateResult;
import dev.sentinel.domain.gate.QualityGate;

import java.util.List;

/**
 * Runs the project's automated tests using the command configured under
 * {@code [quality-gates.tests]}. The command is never hardcoded here.
 */
public class MavenTestGate implements QualityGate {

    public static final String NAME = "tests";

    private final CommandExecutor executor;
    private final List<String> command;

    public MavenTestGate(CommandExecutor executor, List<String> command) {
        this.executor = executor;
        this.command = List.copyOf(command);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public GateResult execute(Project project) {
        return GateResult.from(NAME, command, executor.execute(command, project.root()));
    }
}
