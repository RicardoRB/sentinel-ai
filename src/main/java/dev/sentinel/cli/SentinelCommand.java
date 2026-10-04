package dev.sentinel.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

@Command(name = "sentinel",
        description = "Sentinel is a quality gate orchestrator for AI coding agents.",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        subcommands = {DetectCommand.class, InitCommand.class, CheckCommand.class},
        footer = {"", "Exit codes: 0 = ok, 1 = failed, 2 = error"})
public class SentinelCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(spec.commandLine().getErr());
        return ExitCodes.ERROR;
    }
}
