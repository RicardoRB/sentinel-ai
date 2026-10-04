package dev.sentinel.cli;

import dev.sentinel.application.IntegrationService;
import dev.sentinel.domain.agent.IntegrationResult;
import dev.sentinel.domain.config.SentinelException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import com.google.inject.Inject;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

@Command(name = "integrate", description = "Configure a supported coding-agent integration.",
        mixinStandardHelpOptions = true, subcommands = {})
public final class IntegrateCommand implements Callable<Integer> {
    @Spec private CommandSpec spec;
    @Mixin private ProjectOptions options = new ProjectOptions();
    @Parameters(index = "0", description = "Agent: opencode") private String agent;
    @Option(names = "--remove", description = "Remove only Sentinel-owned integration content.") private boolean remove;
    private final IntegrationService service;

    @Inject
    public IntegrateCommand(IntegrationService service) { this.service = service; }

    @Override public Integer call() {
        try {
            IntegrationResult result = service.integrate(agent, options.directory(), remove);
            PrintWriter out = spec.commandLine().getOut();
            out.printf("%s: %s%n", result.status(), result.message());
            result.changed().forEach(path -> out.println(path));
            return result.status() == IntegrationResult.Status.CONFLICT ? ExitCodes.ERROR : ExitCodes.OK;
        } catch (SentinelException e) {
            throw e;
        }
    }
}
