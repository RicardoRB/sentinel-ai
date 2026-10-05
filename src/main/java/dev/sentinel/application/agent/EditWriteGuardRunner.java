package dev.sentinel.application.agent;

import dev.sentinel.domain.process.CommandExecutor;
import dev.sentinel.domain.process.CommandResult;

import java.nio.file.Path;
import java.util.List;

/** Runs the existing machine-readable Sentinel check for an agent edit/write event. */
public final class EditWriteGuardRunner {
    private static final List<String> CHECK = List.of("sentinel", "check", "--format", "json");

    private final CommandExecutor executor;

    public EditWriteGuardRunner(CommandExecutor executor) {
        this.executor = executor;
    }

    public GuardRunResult run(Path projectRoot) {
        CommandResult result = executor.execute(CHECK, projectRoot);
        String output = result.stdout() == null || result.stdout().isBlank()
                ? result.stderr() : result.stdout();
        return new GuardRunResult(result.succeeded() && !result.hasExecutionError(), output,
                result.exitCode(), result.executionError());
    }

    public record GuardRunResult(boolean succeeded, String output, int exitCode, String executionError) {
    }
}
