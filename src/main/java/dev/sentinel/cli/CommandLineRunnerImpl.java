package dev.sentinel.cli;

import dev.sentinel.domain.config.SentinelException;
import com.google.inject.Inject;
import com.google.inject.Injector;
import picocli.CommandLine;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** Bridges application startup to Picocli and turns the command's result into the process exit code. */
public class CommandLineRunnerImpl {

    private final Injector injector;
    private final SentinelCommand rootCommand;
    private int exitCode;

    @Inject
    public CommandLineRunnerImpl(Injector injector, SentinelCommand rootCommand) {
        this.injector = injector;
        this.rootCommand = rootCommand;
    }

    public int run(String... args) {
        CommandLine commandLine = new CommandLine(rootCommand, new GuiceCommandFactory(injector));
        commandLine.setOut(new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true));
        commandLine.setErr(new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8), true));
        commandLine.setExecutionExceptionHandler(CommandLineRunnerImpl::handle);
        exitCode = commandLine.execute(args);
        commandLine.getOut().flush();
        commandLine.getErr().flush();
        return exitCode;
    }

    /** Known failures print a clean one-line message; anything else is a bug and keeps its stack trace. */
    static int handle(Exception e, CommandLine commandLine, CommandLine.ParseResult parseResult) {
        if (e instanceof SentinelException) {
            commandLine.getErr().println("sentinel: " + e.getMessage());
        } else {
            commandLine.getErr().println("sentinel: unexpected error");
            e.printStackTrace(commandLine.getErr());
        }
        return ExitCodes.ERROR;
    }
}
