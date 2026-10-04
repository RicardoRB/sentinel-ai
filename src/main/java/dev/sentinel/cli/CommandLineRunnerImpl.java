package dev.sentinel.cli;

import dev.sentinel.domain.config.SentinelException;
import picocli.CommandLine;
import com.google.inject.Inject;
import com.google.inject.Injector;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/** Bridges application startup to Picocli and turns the command's result into the process exit code. */
public class CommandLineRunnerImpl {

    private final SentinelCommand rootCommand;
    private final CheckCommand checkCommand;
    private final InitCommand initCommand;
    private final DetectCommand detectCommand;
    private final IntegrateCommand integrateCommand;
    private final DoctorCommand doctorCommand;
    private final Injector injector;

    public CommandLineRunnerImpl(dev.sentinel.application.CheckService checks,
                                 dev.sentinel.application.InitService init,
                                 dev.sentinel.application.ProjectDetector detector,
                                 TextReportRenderer text, JsonReportRenderer json) {
        this.rootCommand = new SentinelCommand();
        this.checkCommand = new CheckCommand(checks, text, json);
        this.initCommand = new InitCommand(init);
        this.detectCommand = new DetectCommand(detector);
        this.integrateCommand = new IntegrateCommand(new dev.sentinel.application.IntegrationService(
                detector, java.util.List.of(new dev.sentinel.application.OpenCodeIntegration())));
        this.doctorCommand = new DoctorCommand(new dev.sentinel.application.DoctorService(detector));
        this.injector = null;
    }

    /** Compatibility constructor for embedding/tests that still use the Guice composition root. */
    @Inject
    public CommandLineRunnerImpl(Injector injector, SentinelCommand rootCommand) {
        this.injector = injector;
        this.rootCommand = rootCommand;
        this.checkCommand = null;
        this.initCommand = null;
        this.detectCommand = null;
        this.integrateCommand = null;
        this.doctorCommand = null;
    }

    public int run(String... args) {
        CommandLine.IFactory factory = injector != null ? new GuiceCommandFactory(injector) : new CommandLine.IFactory() {
            @Override
            public <K> K create(Class<K> type) throws Exception {
                if (type == CheckCommand.class) return type.cast(checkCommand);
                if (type == InitCommand.class) return type.cast(initCommand);
                if (type == DetectCommand.class) return type.cast(detectCommand);
                if (type == IntegrateCommand.class) return type.cast(integrateCommand);
                if (type == DoctorCommand.class) return type.cast(doctorCommand);
                return CommandLine.defaultFactory().create(type);
            }
        };
        CommandLine commandLine = new CommandLine(rootCommand, factory);
        commandLine.setOut(new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true));
        commandLine.setErr(new PrintWriter(new OutputStreamWriter(System.err, StandardCharsets.UTF_8), true));
        commandLine.setExecutionExceptionHandler(CommandLineRunnerImpl::handle);
        int exitCode = commandLine.execute(args);
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
