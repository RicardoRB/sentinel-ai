package dev.sentinel.infrastructure.cli;

public final class ExitCodes {

    /** Everything passed / the command succeeded. */
    public static final int OK = 0;
    /** At least one quality gate failed, or no supported project was detected. */
    public static final int FAILED = 1;
    /** Sentinel could not do its job: bad usage, missing or invalid configuration, no project. */
    public static final int ERROR = 2;

    private ExitCodes() {
    }
}
