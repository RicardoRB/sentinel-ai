package dev.sentinel.domain.process;

import java.time.Duration;

public record CommandResult(int exitCode, String stdout, String stderr, Duration duration) {

    public boolean succeeded() {
        return exitCode == 0;
    }
}
