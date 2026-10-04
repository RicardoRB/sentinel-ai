package dev.sentinel.domain.gate;

import dev.sentinel.domain.process.CommandResult;

import java.time.Duration;
import java.util.List;

public record GateResult(
        String name,
        GateStatus status,
        List<String> command,
        int exitCode,
        Duration duration,
        String stdout,
        String stderr) {

    public static GateResult from(String name, List<String> command, CommandResult result) {
        GateStatus status = result.succeeded() ? GateStatus.PASSED : GateStatus.FAILED;
        return new GateResult(name, status, List.copyOf(command), result.exitCode(), result.duration(),
                result.stdout(), result.stderr());
    }

    public boolean passed() {
        return status == GateStatus.PASSED;
    }
}
